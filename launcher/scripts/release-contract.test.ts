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
  assert.match(smoke, /-Environment\s+@\{\s*FULLMOON_DATA_ROOT\s*=\s*\$dataRoot/s);
  assert.match(smoke, /AddSeconds\(90\)/);
  assert.match(smoke, /\/S/);
  assert.match(smoke, /instances\.json/);
  assert.match(smoke, /fullmoon-managed/);
  assert.match(smoke, /play\.fullmoon\.ink/);
  assert.match(smoke, /installed bundled mod hash/);
});
