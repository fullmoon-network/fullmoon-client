import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const file = (path: string) => new URL(`../../${path}`, import.meta.url);
const ci = readFileSync(file(".github/workflows/ci.yml"), "utf8");
const release = readFileSync(file(".github/workflows/release.yml"), "utf8");
const smokePath = file(".github/scripts/windows-smoke.ps1");

test("CI builds and smoke-tests the Windows installer", () => {
  assert.match(ci, /runs-on:\s*windows-latest/);
  assert.match(ci, /windows-smoke\.ps1/);
});

test("tag releases use the same Windows smoke test before upload", () => {
  assert.match(release, /windows-smoke\.ps1/);
  assert.ok(release.indexOf("windows-smoke.ps1") < release.indexOf("softprops/action-gh-release"));
});

test("the Windows smoke uses a clean profile and checks first-run state", () => {
  assert.ok(existsSync(smokePath));
  const smoke = readFileSync(smokePath, "utf8");

  assert.match(smoke, /FULLMOON_DATA_ROOT/);
  assert.match(smoke, /\/S/);
  assert.match(smoke, /instances\.json/);
  assert.match(smoke, /fullmoon-managed/);
  assert.match(smoke, /play\.fullmoon\.ink/);
  assert.match(smoke, /installed bundled mod hash/);
});

test("CI builds the Linux AppImage and deb on the oldest runner and smoke-tests them", () => {
  assert.match(ci, /linux-bundle:/);
  assert.match(ci, /runs-on:\s*ubuntu-22\.04/);
  assert.match(ci, /--bundles appimage,deb/);
  assert.match(ci, /linux-smoke\.sh/);
  assert.match(ci, /name:\s*fullmoon-linux/);
});

test("tag releases ship Windows and Linux together with one SHA256SUMS over every file", () => {
  assert.match(release, /installer:/);
  assert.match(release, /linux:/);
  assert.match(release, /needs:\s*\[installer, linux\]/);
  assert.equal(release.match(/SHA256SUMS >|> SHA256SUMS/g)?.length, 1);
  assert.ok(release.indexOf("linux-smoke.sh") < release.indexOf("softprops/action-gh-release"));
  assert.ok(release.indexOf("SHA256SUMS over every file") < release.indexOf("softprops/action-gh-release"));
});

test("the Linux smoke uses a clean profile and checks the packaged mod and first-run state", () => {
  const smoke = readFileSync(file(".github/scripts/linux-smoke.sh"), "utf8");
  assert.match(smoke, /FULLMOON_DATA_ROOT/);
  assert.match(smoke, /xdotool/);
  assert.match(smoke, /fullmoon-managed/);
  assert.match(smoke, /play\.fullmoon\.ink/);
  assert.match(smoke, /mod hash/);
});
