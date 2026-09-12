#!/usr/bin/env node
/* Answers one question: is this Azure client id on Minecraft Services'
   allowlist? There is no dashboard for it, so the only honest check is the
   call itself. Signs in with the device code flow and walks the exact chain
   the launcher walks: MSA -> Xbox Live -> XSTS -> Minecraft services.

   Prints statuses and error bodies. Tokens never reach stdout.

   Usage: node check-app-registration.mjs [client-id]        (default: brand.json)
*/
import { readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const SCOPE = "XboxLive.signin offline_access";
const DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
const PROMPT_FILE = "/tmp/fullmoon-appid-check.txt";

// A signal must not leave the code on disk: whoever holds it, together with the public
// client id, can collect the tokens the browser sign-in hands back.
for (const sig of ["SIGINT", "SIGTERM"]) {
  process.on(sig, () => {
    rm(PROMPT_FILE, { force: true }).finally(() => process.exit(130));
  });
}

const here = dirname(fileURLToPath(import.meta.url));

async function clientId() {
  let id = process.argv[2] ?? process.env.MS_CLIENT_ID;
  if (!id) {
    const brand = JSON.parse(await readFile(join(here, "..", "..", "brand.json"), "utf8"));
    id = brand.msClientId;
  }
  if (!id?.trim()) {
    throw new Error("no client id: pass one as an argument, set MS_CLIENT_ID, or fill brand.json");
  }
  return id.trim();
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const postJson = (url, body) =>
  fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json", accept: "application/json" },
    body: JSON.stringify(body),
  });
const postForm = (url, params) =>
  fetch(url, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded", accept: "application/json" },
    body: new URLSearchParams(params),
  });

const XERR = {
  2148916233: "this Microsoft account has no Xbox profile",
  2148916235: "Xbox Live is not available in this account's country",
  2148916236: "adult verification required",
  2148916237: "adult verification required",
  2148916238: "child account — needs a family group",
};

async function msa() {
  const cid = await clientId();
  const dcRes = await postForm(DEVICE_CODE_URL, { client_id: cid, scope: SCOPE });
  if (!dcRes.ok) {
    const body = await dcRes.text();
    throw new Error(`device code request refused (${dcRes.status}): ${body.slice(0, 300)}`);
  }
  const dc = await dcRes.json();

  const prompt = [
    "Sign in as any Microsoft account that owns Minecraft: Java Edition.",
    `  URL:  ${dc.verification_uri}`,
    `  CODE: ${dc.user_code}`,
    "",
  ].join("\n");
  console.log(prompt);
  await rm(PROMPT_FILE, { force: true });
  await writeFile(PROMPT_FILE, prompt, { mode: 0o600 });

  const deadline = Date.now() + dc.expires_in * 1000;
  let interval = Math.max(1, dc.interval ?? 5);
  while (Date.now() < deadline) {
    await sleep(interval * 1000);
    const res = await postForm(TOKEN_URL, {
      client_id: cid,
      grant_type: "urn:ietf:params:oauth:grant-type:device_code",
      device_code: dc.device_code,
    });
    const body = await res.json();
    if (res.ok) return body.access_token;
    if (body.error === "authorization_pending") continue;
    if (body.error === "slow_down") { interval += 5; continue; }
    throw new Error(`token endpoint: ${body.error}: ${body.error_description}`);
  }
  throw new Error("the sign-in code expired — run again");
}

async function main() {
  const cid = await clientId();
  console.log(`client id: ${cid}\n`);

  console.log("== 1/5 MSA device code sign-in ==");
  let msaToken;
  try {
    msaToken = await msa();
  } finally {
    await rm(PROMPT_FILE, { force: true });
  }
  console.log(`MSA token received (${msaToken.length} chars)\n`);

  console.log("== 2/5 Xbox Live ==");
  const xblRes = await postJson(XBL_URL, {
    Properties: { AuthMethod: "RPS", SiteName: "user.auth.xboxlive.com", RpsTicket: `d=${msaToken}` },
    RelyingParty: "http://auth.xboxlive.com",
    TokenType: "JWT",
  });
  const xbl = await xblRes.json().catch(() => ({}));
  if (!xblRes.ok) throw new Error(`Xbox Live refused (${xblRes.status}): ${JSON.stringify(xbl)}`);
  const uhs = xbl.DisplayClaims?.xui?.[0]?.uhs;
  if (!uhs) throw new Error("Xbox Live returned no user hash");
  console.log(`ok (${xblRes.status}) uhs=…${String(uhs).slice(-4)}\n`);

  console.log("== 3/5 XSTS ==");
  const xstsRes = await postJson(XSTS_URL, {
    Properties: { SandboxId: "RETAIL", UserTokens: [xbl.Token] },
    RelyingParty: "rp://api.minecraftservices.com/",
    TokenType: "JWT",
  });
  const xsts = await xstsRes.json().catch(() => ({}));
  if (!xstsRes.ok) {
    const xerr = xsts.XErr ?? 0;
    throw new Error(`XSTS rejected (${xstsRes.status}, XErr ${xerr}): ${XERR[xerr] ?? "see XErr"}`);
  }
  if (!xsts.Token) throw new Error("XSTS returned no token");
  console.log(`ok (${xstsRes.status})\n`);

  console.log("== 4/5 Minecraft services login_with_xbox ==");
  const loginRes = await postJson(MC_LOGIN_URL, {
    identityToken: `XBL3.0 x=${uhs};${xsts.Token}`,
  });
  const loginBody = await loginRes.text();
  console.log(`status ${loginRes.status}`);
  if (!loginRes.ok) {
    console.log(`body: ${loginBody}`);
    const allowlisted = !loginBody.includes("Invalid app registration");
    console.log(
      allowlisted
        ? "\nVERDICT: refused, but NOT for the allowlist — read the body above.\n"
        : "\nVERDICT: NOT on the allowlist yet (Invalid app registration).\n",
    );
    process.exitCode = 1;
    return;
  }
  let mcToken;
  try {
    mcToken = JSON.parse(loginBody);
  } catch {
    throw new Error("login_with_xbox returned 200 but the body was not JSON");
  }
  if (!mcToken?.access_token) throw new Error("login_with_xbox returned 200 without an access_token");
  console.log(`accepted — token received (${mcToken.access_token.length} chars)`);
  console.log("\nVERDICT: ALLOWLISTED — login_with_xbox accepted this client id.\n");

  console.log("== 5/5 Minecraft profile ==");
  const profRes = await fetch(MC_PROFILE_URL, {
    headers: { accept: "application/json", authorization: `Bearer ${mcToken.access_token}` },
  });
  if (profRes.ok) {
    const prof = await profRes.json();
    console.log(`ok (${profRes.status}) profile: ${prof.name}`);
  } else if (profRes.status === 404) {
    console.log("404 — this account does not own Minecraft: Java Edition (allowlist still passes)");
  } else {
    console.log(`status ${profRes.status}: ${await profRes.text()}`);
  }
}

main().catch((e) => {
  const cause = e.cause?.message ? ` (${e.cause.message})` : "";
  console.error(`FAILED: ${e.message}${cause}`);
  process.exitCode = 1;
});
