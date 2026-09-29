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

  // the glide: click and photograph the indicator on its way, then at rest
  await page.mouse.down();
  await page.mouse.up();
  for (let i = 0; i < 4; i++) {
    await page.screenshot({ path: `${OUT}/glide-rail-0${i}.png` });
    await sleep(40);
  }
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
} finally {
  writeFileSync(`${OUT}/systemshot.log`, log.join("\n") + "\n");
  console.log(log.join("\n"));
  await browser.close();
}
