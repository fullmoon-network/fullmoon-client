#!/usr/bin/env bash
# Provision the Lightning Studio for tools/capture.py, as the --setup of every studio-capture.sh run.
#
# The Studio's root filesystem is reset when it restarts, so nothing installed here can be assumed
# to still be there: every step checks before it acts, and a warm Studio gets through this in a
# second. The home directory does persist, but it is a FUSE volume that keeps neither unix modes
# nor empty directories, so what has to stay executable (the JDK) and what is written per run live
# under /tmp instead.
#
# What capture.py needs and the image does not have: an X server with GLX and XTEST, a GL that
# works without a GPU — Mesa's llvmpipe — the X client libraries GLFW opens at runtime, python-xlib
# for the system python, and a JDK 25, because the mod's toolchain is 25 and Gradle is not allowed
# to provision one.
set -euo pipefail

STATE=/tmp/fmc-capture
mkdir -p "$STATE"

packages=(
    xvfb mesa-utils
    libgl1 libglx-mesa0 libgl1-mesa-dri libegl1
    libx11-6 libxext6 libxrender1 libxrandr2 libxcursor1 libxi6 libxinerama1 libxxf86vm1
    python3-xlib
)
missing=()
for package in "${packages[@]}"; do
    dpkg-query -W -f='${Status}' "$package" 2>/dev/null | grep -q 'install ok installed' \
        || missing+=("$package")
done
if ((${#missing[@]})); then
    echo "studio-setup: installing ${missing[*]}"
    sudo apt-get update -qq
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends \
        "${missing[@]}" >/dev/null
fi
/usr/bin/python3 -c 'import Xlib.ext.xtest' \
    || { echo "studio-setup: python3-xlib is installed but does not import" >&2; exit 1; }

# Xvfb makes this itself when it is missing, but as an unprivileged user it cannot make it root's.
if [ ! -d /tmp/.X11-unix ]; then
    sudo install -d -m 1777 -o root -g root /tmp/.X11-unix
fi

is_jdk25() {
    [ -x "$1/bin/java" ] && "$1/bin/java" -version 2>&1 | grep -q '^[a-z]* version "25'
}

jdk=""
for candidate in "$STATE"/jdk/jdk-25* "$HOME"/.jdks/jdk-25* /usr/lib/jvm/*-25-*; do
    if is_jdk25 "$candidate"; then
        jdk=$candidate
        break
    fi
done
if [ -z "$jdk" ]; then
    echo "studio-setup: fetching Temurin 25 into $STATE/jdk"
    rm -rf "$STATE/jdk" && mkdir -p "$STATE/jdk"
    release=$(wget -qO- 'https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse')
    link=$(python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["binary"]["package"]["link"])' <<<"$release")
    sum=$(python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["binary"]["package"]["checksum"])' <<<"$release")
    wget -qO "$STATE/jdk/jdk.tar.gz" "$link"
    echo "$sum  $STATE/jdk/jdk.tar.gz" | sha256sum -c --quiet -
    tar xzf "$STATE/jdk/jdk.tar.gz" -C "$STATE/jdk"
    rm "$STATE/jdk/jdk.tar.gz"
    for candidate in "$STATE"/jdk/jdk-25*; do
        is_jdk25 "$candidate" && jdk=$candidate
    done
    [ -n "$jdk" ] || { echo "studio-setup: no working JDK 25 after the download" >&2; exit 1; }
fi

printf 'export JAVA_HOME=%q\n' "$jdk" > "$STATE/env"
echo "studio-setup: ready in ${SECONDS}s — xvfb $(dpkg-query -W -f='${Version}' xvfb)," \
    "mesa $(dpkg-query -W -f='${Version}' libgl1-mesa-dri), JDK $jdk"
