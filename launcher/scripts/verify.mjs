/* verify.mjs — drives the Fullmoon Launcher in headless Chrome or Firefox and
   screenshots the full user journey:
   1. Home dashboard
   2. Mods browser
   3. Cosmetics 3D preview
   4. Accounts manager
   5. Settings (General & Java)
   6. Settings HUD editor
   7. Command palette (Ctrl+K)
   8. Launch overlay
   9. Clean-profile one-click offline account

   Chromium stays the default. Set BROWSER_PATH to a Firefox binary on hosts
   without Chromium; the launch adapts to the engine behind that path. */

import puppeteer from "puppeteer-core";
import { setTimeout as sleep } from "node:timers/promises";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const findBrowser = () => {
  if (process.env.BROWSER_PATH && fs.existsSync(process.env.BROWSER_PATH)) {
    return process.env.BROWSER_PATH;
  }
  const candidates = [
    "/home/person/.cache/ms-playwright/chromium-1234/chrome-linux64/chrome",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  ];
  for (const c of candidates) {
    if (fs.existsSync(c)) return c;
  }
  throw new Error("No compatible Chrome/Chromium or Firefox browser found");
};

const BROWSER_PATH = findBrowser();
const IS_FIREFOX = /firefox/i.test(path.basename(BROWSER_PATH));
const HEADLESS = process.env.HEADLESS !== "0";
const URL = "http://127.0.0.1:5921";
const OUT = process.env.OUT_DIR || "/tmp/fullmoon-launcher-shots";
const PROFILE_DIR =
  process.env.PROFILE_DIR || fs.mkdtempSync(path.join(os.tmpdir(), "fullmoon-verify-"));

if (!fs.existsSync(OUT)) {
  fs.mkdirSync(OUT, { recursive: true });
}

const shot = async (page, name) => {
  const filePath = path.join(OUT, `fullmoon-launcher-${name}.png`);
  await page.screenshot({ path: filePath });
  console.log("shot:", name, "->", filePath);
};

const clickNav = async (page, text) => {
  await page.evaluate((t) => {
    const btns = [...document.querySelectorAll("button, .nav-item, [role='button']")];
    const target = btns.find((b) => b.textContent && b.textContent.trim().includes(t));
    if (target) {
      target.click();
    } else {
      throw new Error(`Nav item not found: ${t}`);
    }
  }, text);
};

const browser = await puppeteer.launch({
  executablePath: BROWSER_PATH,
  ...(IS_FIREFOX
    ? {
        browser: "firefox",
        firefoxUserPrefs: {
          "webgl.disabled": false,
          "webgl.force-enabled": true,
          "webgl.forbid-software": false,
          "gfx.webrender.software": true,
        },
      }
    : {}),
  headless: IS_FIREFOX ? HEADLESS : "new",
  userDataDir: PROFILE_DIR,
  args: IS_FIREFOX
    ? []
    : ["--window-size=1600,1000", "--force-device-scale-factor=1", "--no-sandbox"],
  defaultViewport: { width: 1600, height: 1000 },
});

try {
  const page = await browser.newPage();
  page.on("pageerror", (e) => console.log("PAGE ERROR:", e.message));
  page.on("console", (m) => {
    if (m.type() === "error") console.log("CONSOLE ERROR:", m.text());
  });

  await page.goto(URL, { waitUntil: "networkidle0", timeout: 20000 });
  await sleep(1500);

  // 1. Play Screen (Centered Hero Launchpad)
  await shot(page, "01-play");

  // 1b. Dashboard Screen (Servers Overview)
  await clickNav(page, "대시보드");
  await sleep(1000);
  await shot(page, "01b-dashboard-servers");

  // 1c. Dashboard Screen (Wallet Analytics)
  await page.click("#dash-tab-wallet");
  await sleep(600);
  await shot(page, "01c-dashboard-wallet");

  // 1d. Dashboard Screen (News Feed)
  await page.click("#dash-tab-news");
  await sleep(600);
  await shot(page, "01d-dashboard-news");

  // 2. Mods Screen
  await clickNav(page, "모드");
  await sleep(800);
  await shot(page, "02-mods");

  // 3. Cosmetics Screen with 3D Canvas
  await clickNav(page, "코스메틱");
  await sleep(1500);
  await shot(page, "03-cosmetics");

  // 4. Accounts Screen
  await clickNav(page, "계정");
  await sleep(800);
  await shot(page, "04-accounts");

  // 5. Settings Screen (General)
  await clickNav(page, "설정");
  await sleep(800);
  await shot(page, "05-settings-general");

  // 6. Settings HUD Tab
  await page.evaluate(() => {
    const tabs = [...document.querySelectorAll("button, [role='tab']")];
    const hudTab = tabs.find((t) => t.textContent && t.textContent.includes("HUD"));
    if (hudTab) hudTab.click();
  });
  await sleep(800);
  await shot(page, "06-settings-hud");

  // 7. Command Palette (Ctrl+K)
  await page.keyboard.down("Control");
  await page.keyboard.press("KeyK");
  await page.keyboard.up("Control");
  await sleep(600);
  await shot(page, "07-command-palette");

  // Close Palette (Esc)
  await page.keyboard.press("Escape");
  await sleep(400);

  // 8. Launch Play
  await clickNav(page, "플레이");
  await sleep(600);
  await page.evaluate(() => {
    const playBtn =
      document.querySelector(".massive-play-button") ||
      document.querySelector(".playbtn-go") ||
      [...document.querySelectorAll("button")].find(
        (b) => b.textContent && (b.textContent.includes("PLAY NOW") || b.textContent.includes("플레이")),
      );
    if (playBtn) playBtn.click();
  });
  await sleep(1200);
  await shot(page, "08-launch-overlay");

  const offlinePage = await browser.newPage();
  await offlinePage.goto(URL, { waitUntil: "networkidle0", timeout: 20000 });
  await offlinePage.evaluate(() => {
    localStorage.setItem("pinion.v1.state", JSON.stringify({ accounts: [], activeUuid: null }));
  });
  await offlinePage.reload({ waitUntil: "networkidle0", timeout: 20000 });
  await sleep(800);
  await clickNav(offlinePage, "계정");
  await sleep(500);
  await shot(offlinePage, "09-offline-account-start");
  await offlinePage.evaluate(() => {
    const button = [...document.querySelectorAll("button")].find(
      (candidate) => candidate.textContent?.includes("로컬 테스트 계정 만들기"),
    );
    if (!(button instanceof HTMLButtonElement)) {
      throw new Error("Local test account button not found");
    }
    button.click();
  });
  await offlinePage.waitForFunction(
    () => {
      const state = JSON.parse(localStorage.getItem("pinion.v1.state") ?? "{}");
      return state.accounts?.length === 1 && state.accounts[0].username === "FullmoonTest";
    },
    { timeout: 5000 },
  );
  await offlinePage.waitForSelector(".acc-hero", { timeout: 5000 });
  const localState = await offlinePage.evaluate(() =>
    JSON.parse(localStorage.getItem("pinion.v1.state") ?? "{}"),
  );
  if (localState.accounts?.length !== 1 || localState.accounts[0].username !== "FullmoonTest") {
    throw new Error(`Unexpected local account state: ${JSON.stringify(localState.accounts)}`);
  }
  await shot(offlinePage, "09b-offline-account-created");

  console.log("FULLMOON LAUNCHER VERIFICATION COMPLETED SUCCESSFULLY");
} finally {
  await browser.close();
}
