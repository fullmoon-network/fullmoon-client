/* systemshot.mjs — shoot the built launcher (vite preview, mock core) in headless Chrome at the
   mock's size, 1440×900 at device scale 2, for the real-versus-mock pairs of the redesign.

     BROWSER_PATH=/usr/bin/google-chrome SHOT_DIR=shots URL=http://127.0.0.1:5921 node scripts/systemshot.mjs

   Shots: the play screen at rest and with a rail item under the pointer, four frames of the rail's
   selection gliding from 플레이 to 대시보드 (~40 ms apart), and every other screen. */
import { mkdirSync, existsSync, writeFileSync } from "node:fs";
import { setTimeout as sleep } from "node:timers/promises";
import puppeteer from "puppeteer-core";

const URL = process.env.URL ?? "http://127.0.0.1:5921";
const OUT = process.env.SHOT_DIR ?? "shots";
const BROWSER =
  process.env.BROWSER_PATH ??
  ["/usr/bin/google-chrome", "/usr/bin/chromium-browser", "/usr/bin/chromium"].find((p) => existsSync(p));
if (!BROWSER) throw new Error("no Chrome; set BROWSER_PATH");
mkdirSync(OUT, { recursive: true });

const browser = await puppeteer.launch({
  executablePath: BROWSER,
  headless: "new",
  args: ["--no-sandbox", "--disable-gpu", "--use-gl=angle", "--use-angle=swiftshader", "--hide-scrollbars", "--font-render-hinting=none"],
  defaultViewport: { width: 1440, height: 900, deviceScaleFactor: 2 },
});
const log = [];
try {
  const page = await browser.newPage();
  page.on("pageerror", (e) => log.push(`PAGE ERROR: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error") log.push(`CONSOLE ERROR: ${m.text()}`);
  });
  await page.goto(URL, { waitUntil: "networkidle0", timeout: 30000 });
  await page.evaluate(() => document.fonts.ready);
  // the staged reveal and the screen fade are over well inside a second
  await sleep(1200);

  const shot = async (name) => {
    await page.screenshot({ path: `${OUT}/${name}.png` });
    log.push(`shot ${name}`);
  };
  const railItem = (label) =>
    page.evaluateHandle((want) => {
      return [...document.querySelectorAll(".rail-item")].find((b) => b.textContent?.trim() === want) ?? null;
    }, label);
  const nav = async (label, settle = 900) => {
    const el = await railItem(label);
    await el.click();
    await sleep(settle);
  };

  await page.mouse.move(1200, 850);
  await shot("play");

  // the pointer on 대시보드: a lift, and the current item stays where it is
  const dash = await railItem("대시보드");
  const box = await dash.boundingBox();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await sleep(250);
  await shot("play-hover");

  // The glide, frame by frame. A screenshot at this size takes longer than the whole 140 ms
  // transition, so the rig pauses every running animation the moment the rail's indicator starts
  // moving and steps them all through their timelines: each frame is the state at that instant.
  const started = await page.evaluate(async () => {
    [...document.querySelectorAll(".rail-item")].find((b) => b.textContent?.trim() === "대시보드")?.click();
    const t0 = performance.now();
    while (performance.now() - t0 < 1500) {
      await new Promise((r) => requestAnimationFrame(r));
      const moving = document.getAnimations().some((a) => a.effect?.target?.classList?.contains("glide"));
      if (moving) {
        for (const a of document.getAnimations()) {
          a.pause();
          a.currentTime = 0;
        }
        return true;
      }
    }
    return false;
  });
  log.push(`glide transition caught: ${started}`);
  const STEPS = [0, 15, 30, 45, 70, 100, 140];
  writeFileSync(`${OUT}/glide-rail.json`, JSON.stringify({ ms: STEPS }));
  for (const [i, ms] of STEPS.entries()) {
    await page.evaluate((t) => {
      for (const a of document.getAnimations()) a.currentTime = t;
    }, ms);
    await page.screenshot({ path: `${OUT}/glide-rail-${String(i).padStart(2, "0")}.png` });
  }
  await page.evaluate(() => {
    for (const a of document.getAnimations()) {
      try {
        a.finish();
      } catch {
        a.play();
      }
    }
  });
  await sleep(900);
  await shot("dashboard");

  await nav("모드");
  await shot("mods");
  await nav("코스메틱", 1600);
  await shot("cosmetics");
  await nav("계정");
  await shot("accounts");
  await nav("설정");
  await page.evaluate(() => {
    const b = [...document.querySelectorAll(".set-nav button")].find((x) => x.textContent?.includes("외관"));
    b?.click();
  });
  await sleep(700);
  await shot("settings-look");

  await page.keyboard.down("Control");
  await page.keyboard.press("KeyK");
  await page.keyboard.up("Control");
  await sleep(500);
  await shot("palette");
  await page.keyboard.press("Escape");

  await nav("플레이");
  await page.mouse.move(1200, 850);
  await sleep(600);
  await shot("play-again");

  // the live state: the browser build cannot open a socket, so the rig hands the mock core the
  // answer a lobby would give (fixture, labelled as such wherever it is shown)
  await page.evaluate(() => localStorage.setItem("fullmoon.rig.ping", JSON.stringify({ players: 12, maxPlayers: 60, pingMs: 24 })));
  await page.reload({ waitUntil: "networkidle0" });
  await page.evaluate(() => document.fonts.ready);
  await page.mouse.move(1200, 850);
  await sleep(1400);
  await shot("play-live");
} finally {
  writeFileSync(`${OUT}/systemshot.log`, log.join("\n") + "\n");
  console.log(log.join("\n"));
  await browser.close();
}
