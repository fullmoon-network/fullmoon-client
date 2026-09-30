#!/usr/bin/env bash
# Linux counterpart of windows-smoke.ps1. Runs on the runner after
# `tauri build --bundles appimage,deb`: checks what the packages contain, then
# starts the AppImage on a clean profile under Xvfb and asserts that it stays up,
# opens a window, writes its first-run state and finds a Java. Leaves
# linux-smoke.png and linux-smoke-result.txt in the working directory.
set -euo pipefail

bundle=launcher/src-tauri/target/release/bundle
staged=launcher/src-tauri/resources/mods/fullmoon-client.jar
fail() { echo "SMOKE FAIL: $*" >&2; exit 1; }

shopt -s nullglob
appimages=("$bundle"/appimage/*.AppImage)
debs=("$bundle"/deb/*.deb)
[ "${#appimages[@]}" -eq 1 ] || fail "expected one AppImage, got ${#appimages[@]}"
[ "${#debs[@]}" -eq 1 ] || fail "expected one deb, got ${#debs[@]}"
appimage=$(realpath "${appimages[0]}")
chmod +x "$appimage"

# the deb carries the mod, byte for byte
work=$(mktemp -d)
dpkg-deb -x "${debs[0]}" "$work/deb"
mapfile -t inpkg < <(find "$work/deb" -name fullmoon-client.jar)
[ "${#inpkg[@]}" -eq 1 ] || fail "expected one bundled mod in the deb, got ${#inpkg[@]}"
[ "$(sha256sum < "${inpkg[0]}")" = "$(sha256sum < "$staged")" ] || fail "deb mod hash differs from the staged jar"
[ -x "$work/deb/usr/bin/fullmoon" ] || fail "deb has no /usr/bin/fullmoon"
echo "deb ok: $(dpkg-deb -f "${debs[0]}" Package Version Depends | tr '\n' ' ')" | tee -a linux-smoke-result.txt

# the AppImage too (runner has no FUSE: extract, then look inside)
( cd "$work" && "$appimage" --appimage-extract >/dev/null )
mapfile -t inimg < <(find "$work/squashfs-root" -name fullmoon-client.jar)
[ "${#inimg[@]}" -eq 1 ] || fail "expected one bundled mod in the AppImage, got ${#inimg[@]}"
[ "$(sha256sum < "${inimg[0]}")" = "$(sha256sum < "$staged")" ] || fail "AppImage mod hash differs from the staged jar"
echo "appimage ok: mod hash matches" | tee -a linux-smoke-result.txt

data="$work/profile"
mkdir -p "$data"
export FULLMOON_DATA_ROOT="$data"
export APPIMAGE_EXTRACT_AND_RUN=1
# software rendering: the runner has no GPU
export WEBKIT_DISABLE_COMPOSITING_MODE=1 WEBKIT_DISABLE_DMABUF_RENDERER=1 LIBGL_ALWAYS_SOFTWARE=1

Xvfb :99 -screen 0 1440x900x24 >/dev/null 2>&1 &
xvfb=$!
export DISPLAY=:99
sleep 2
dbus-run-session -- "$appimage" >launcher.log 2>&1 &
app=$!

sleep 20
kill -0 "$app" 2>/dev/null || { cat launcher.log; fail "the launcher exited within 20 s"; }

win=""
for _ in 1 2 3 4 5 6; do
  win=$(xdotool search --onlyvisible --name 'Fullmoon' 2>/dev/null | head -n1 || true)
  [ -n "$win" ] && break
  sleep 2
done
xwininfo -root -tree > x-tree.txt || true
import -window root linux-smoke.png || true
[ -n "$win" ] || { cat launcher.log; cat x-tree.txt; fail "no visible window named Fullmoon"; }
echo "window ok: id $win $(xdotool getwindowgeometry "$win" | tr '\n' ' ')" | tee -a linux-smoke-result.txt

[ -f "$data/instances.json" ] || { cat launcher.log; ls -la "$data"; fail "no instances.json — first run never happened"; }
grep -q 'fullmoon-managed' "$data/instances.json" || fail "instances.json has no fullmoon-managed instance"
grep -q 'play.fullmoon.ink' "$data/instances.json" || fail "the managed instance has no play.fullmoon.ink quick-play server"
grep -q '26.1.2' "$data/instances.json" || fail "the managed instance is not Minecraft 26.1.2"
echo "first-run state ok" | tee -a linux-smoke-result.txt

if [ -f "$data/settings.json" ]; then
  echo "settings: $(tr -d '\n ' < "$data/settings.json" | head -c 400)" | tee -a linux-smoke-result.txt
  grep -q '"javaPath":"[^"]*/bin/java"' <(tr -d '\n ' < "$data/settings.json") || fail "the launcher found no Java although JAVA_HOME is set"
else
  fail "no settings.json — the frontend never asked for settings"
fi

kill -0 "$app" 2>/dev/null || fail "the launcher died after the window opened"
kill "$app" 2>/dev/null || true
kill "$xvfb" 2>/dev/null || true
echo "SMOKE OK" | tee -a linux-smoke-result.txt
