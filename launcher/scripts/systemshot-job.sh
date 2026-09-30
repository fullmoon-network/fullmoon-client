#!/usr/bin/env bash
# The whole evidence job for the launcher, run from the repository root on a runner with Chrome:
# install, test, build, serve the build, photograph it (systemshot.mjs), render the staged mockup
# with the same Chrome when one is present at launcher/shots/mock, and pack launcher/shots.tgz.
# Node 22.6+ is needed for the tests' --experimental-strip-types; an older runner gets one in /tmp.
set -euo pipefail
cd "$(dirname "$0")/../.."
ROOT=$(pwd)

need_node() { node -e 'const [a,b]=process.versions.node.split(".").map(Number); process.exit(a>22||(a==22&&b>=6)?0:1)'; }
if ! need_node; then
  V=v22.12.0
  curl -fsSL "https://nodejs.org/dist/$V/node-$V-linux-x64.tar.xz" -o /tmp/node.tar.xz
  mkdir -p /tmp/node && tar xJf /tmp/node.tar.xz -C /tmp/node --strip-components=1
  export PATH=/tmp/node/bin:$PATH
fi
node --version; npm --version

CHROME=$(command -v google-chrome || command -v google-chrome-stable || command -v chromium-browser || command -v chromium || true)
[ -n "$CHROME" ] || { echo "no chrome on this runner" >&2; exit 3; }
"$CHROME" --version

cd launcher
npm ci --no-audit --no-fund
TEST_RC=0
npm test || TEST_RC=$?
echo "npm test rc=$TEST_RC"
npm run build

rm -rf shots/*.png shots/*.log
mkdir -p shots
(npx vite preview --host 127.0.0.1 --port 5921 --strictPort > shots/preview.log 2>&1 &)
for _ in $(seq 1 40); do curl -fs http://127.0.0.1:5921/ >/dev/null 2>&1 && break; sleep 0.5; done
BROWSER_PATH=$CHROME SHOT_DIR=shots node scripts/systemshot.mjs

if [ -f shots/mock/g-launcher.html ]; then
  "$CHROME" --headless=new --no-sandbox --disable-gpu --hide-scrollbars --disable-dev-shm-usage \
    --font-render-hinting=none --window-size=1440,900 --force-device-scale-factor=2 \
    --screenshot="$ROOT/launcher/shots/mock-g-launcher.png" "file://$ROOT/launcher/shots/mock/g-launcher.html" 2>&1 \
    | grep -E 'bytes written|rror' | grep -viE 'dbus|fontconfig|gpu|vulkan|MESA' || true
fi
fc-list | grep -ciE 'pretendard|hahmlet' || true
tar czf shots.tgz shots
ls -la shots.tgz shots
exit "$TEST_RC"
