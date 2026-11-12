import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path: string) => readFileSync(new URL(`../../${path}`, import.meta.url), "utf8");

const LAUNCHER_VERSION = "1.3.0";
const MOD_VERSION = "3.3.0";

test("every launcher manifest agrees on the release version", () => {
  const pkg = JSON.parse(read("launcher/package.json"));
  const lock = JSON.parse(read("launcher/package-lock.json"));
  const tauri = JSON.parse(read("launcher/src-tauri/tauri.conf.json"));

  assert.equal(pkg.version, LAUNCHER_VERSION);
  assert.equal(lock.version, LAUNCHER_VERSION);
  assert.equal(lock.packages[""].version, LAUNCHER_VERSION);
  assert.equal(tauri.version, LAUNCHER_VERSION);
});

test("the Rust package and its lock entry agree with the launcher", () => {
  const cargo = read("launcher/src-tauri/Cargo.toml");
  const cargoVersion = cargo.match(/\[package\][\s\S]*?\nversion = "([^"]+)"/)?.[1];
  const lock = read("launcher/src-tauri/Cargo.lock");
  const pinionVersion = lock.match(
    /\[\[package\]\]\nname = "pinion"\nversion = "([^"]+)"/,
  )?.[1];

  assert.equal(cargoVersion, LAUNCHER_VERSION);
  assert.equal(pinionVersion, LAUNCHER_VERSION);
});

test("the bundled mod catalogue names the stable mod build, not the launcher", () => {
  const catalog = JSON.parse(read("launcher/src-tauri/resources/catalog.json"));
  const mod = catalog.mods.find((entry: { id: string }) => entry.id === "fullmoon-client");

  assert.ok(mod, "the catalogue must list fullmoon-client");
  assert.equal(mod.version, MOD_VERSION);
});

test("the beta GUI and skin pack are bundled by default", () => {
  const catalog = JSON.parse(read("launcher/src-tauri/resources/catalog.json"));
  const pack = catalog.packs.find((entry: { id: string }) => entry.id === "fullmoon-gui");

  assert.ok(pack, "the catalogue must list fullmoon-gui");
  assert.match(pack.sha1Url, /^https:\/\/.+\/fullmoon-gui\.sha1$/);
  assert.match(pack.url, /^https:\/\/.+\/fullmoon-gui\.zip$/);
  assert.equal(pack.game, "26.1.2");
  assert.equal(pack.bundled, true);
});
