#!/usr/bin/env bash
# The AppImage linuxdeploy builds carries the build host's (Ubuntu 22.04) copies of the
# graphics stack. On a newer host — Fedora 44, Arch — WebKitWebProcess then loads that old
# libEGL/libwayland next to the host's new Mesa driver and dies with
#   Could not create default EGL display: EGL_BAD_PARAMETER. Aborting...
# so the window stays blank. Take the graphics stack back out of the AppDir and let the host
# supply it (every desktop has these; the AppImage excludelist says the same for libGL/libEGL),
# then pack the AppDir again.
set -euo pipefail

bundle=launcher/src-tauri/target/release/bundle/appimage
shopt -s nullglob
appdirs=("$bundle"/*.AppDir)
[ "${#appdirs[@]}" -eq 1 ] || { echo "expected one AppDir in $bundle, got ${#appdirs[@]}" >&2; exit 1; }
appdir=${appdirs[0]}
images=("$bundle"/*.AppImage)
[ "${#images[@]}" -eq 1 ] || { echo "expected one AppImage in $bundle" >&2; exit 1; }
image=${images[0]}

echo "graphics-stack libraries bundled before:"
find "$appdir" \( -name 'libEGL*' -o -name 'libGL.so*' -o -name 'libGLX*' -o -name 'libGLdispatch*' \
  -o -name 'libGLESv2*' -o -name 'libOpenGL*' -o -name 'libgbm*' -o -name 'libdrm*' -o -name 'libglapi*' \
  -o -name 'libwayland-*' -o -name 'libxcb-dri*' -o -name 'libxshmfence*' \) -print | sort

find "$appdir" \( -name 'libEGL*' -o -name 'libGL.so*' -o -name 'libGLX*' -o -name 'libGLdispatch*' \
  -o -name 'libGLESv2*' -o -name 'libOpenGL*' -o -name 'libgbm*' -o -name 'libdrm*' -o -name 'libglapi*' \
  -o -name 'libwayland-*' -o -name 'libxcb-dri*' -o -name 'libxshmfence*' \) -print -delete \
  | sed 's/^/removed: /' > appimage-removed-libs.txt
wc -l < appimage-removed-libs.txt | sed 's/^/removed libraries: /'

tool=$(mktemp -d)/appimagetool
wget -q -O "$tool" https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage
chmod +x "$tool"
# keep the file name Tauri chose, and its executable bit
ARCH=x86_64 "$tool" --appimage-extract-and-run --no-appstream "$appdir" "$image.new"
mv "$image.new" "$image"
chmod +x "$image"
ls -l "$image"
