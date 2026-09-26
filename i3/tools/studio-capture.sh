#!/usr/bin/env bash
# Run tools/capture.py in the Lightning Studio and bring the frames home.
#
#   tools/studio-capture.sh OUTDIR [capture.py options] NAME:KEYS...
#
# The arguments are capture.py's own. The box this repo is worked on stops any build-class process,
# and a capture is a Gradle build, a JVM and a software rasteriser, so it runs in the Studio through
# spectre-offload (STUDIO_OFFLOAD names another wrapper with the same interface). What comes back
# into OUTDIR is what capture.py writes — one PNG per shot and run.log — plus build.log from the
# warm-up, glinfo.txt from the GL the frames were drawn with, and timings.txt.
#
# Only i3/mod and i3/tools are uploaded, from the working tree and without build output, so a
# capture is of what is on disk, not of what is committed. In the Studio the tree is copied under
# /tmp before anything runs: the home the upload lands in is a FUSE volume that keeps no unix
# modes and is slow on small files, and /tmp keeps the build warm between runs until the Studio
# restarts.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)

in_studio() {
    local tarball=$1
    shift
    local state=/tmp/fmc-capture
    local work=$state/tree out=$state/out
    local started=$SECONDS mark
    # shellcheck disable=SC1091
    . "$state/env"
    export JAVA_HOME
    # A single-use daemon lives in capture.py's process group and dies with it; a shared one would
    # outlive the run, holding its heap in a Studio other builds are waiting for.
    export GRADLE_OPTS="-Dorg.gradle.daemon=false"

    pkill -f "^Xvfb :(9|19) " || true
    pkill -f -- "$work/i3/mod/" || true
    rm -rf "$out"
    mkdir -p "$work" "$out"
    rsync -a --delete --exclude build/ --exclude .gradle/ --exclude run/ ./ "$work/"
    chmod +x "$work/i3/mod/gradlew"
    # run/ is not in the repo and the upload never has one, so every Studio run is a first launch,
    # and a first launch opens on the accessibility onboarding screen instead of the title screen.
    rm -rf "$work/i3/mod/run"
    mkdir -p "$work/i3/mod/run"
    printf 'onboardAccessibility:false\n' > "$work/i3/mod/run/options.txt"

    Xvfb :19 -screen 0 64x64x24 +extension GLX -nolisten tcp >/dev/null 2>&1 &
    local probe=$!
    sleep 1
    DISPLAY=:19 glxinfo -B > "$out/glinfo.txt" 2>&1 || true
    kill "$probe" 2>/dev/null || true
    grep -E 'renderer string|core profile version string' "$out/glinfo.txt" || true

    # Downloads and compilation happen here, so capture.py's 240 s wait for the atlases measures a
    # client starting, not a cold Gradle cache filling on a volume that is slow to write.
    mark=$SECONDS
    local rc=0
    (cd "$work/i3/mod" && ./gradlew -p . classes configureClientLaunch --console=plain) \
        > "$out/build.log" 2>&1 || rc=$?
    echo "build $((SECONDS - mark))s rc=$rc" >> "$out/timings.txt"
    tail -n 3 "$out/build.log"

    if ((rc == 0)); then
        mark=$SECONDS
        { /usr/bin/python3 "$work/i3/tools/capture.py" "$out" "$@" 2>&1 | tee "$out/capture.txt"; } \
            || rc=$?
        echo "capture $((SECONDS - mark))s rc=$rc" >> "$out/timings.txt"
        if [ -d "$work/i3/mod/run/crash-reports" ]; then
            cp -r "$work/i3/mod/run/crash-reports" "$out/"
        fi
    fi
    pkill -f "^Xvfb :9 " || true
    pkill -f -- "$work/i3/mod/" || true
    echo "studio $((SECONDS - started))s" >> "$out/timings.txt"
    tar czf "$tarball" -C "$out" .
    # 5, 6 and 255 are the offload wrapper's own words for "the Studio was not there".
    case $rc in 5|6|255) rc=1 ;; esac
    return "$rc"
}

if [ "${1:-}" = "--in-studio" ]; then
    shift
    in_studio "$@"
    exit
fi

if (($# < 2)) || [[ $1 == -* ]]; then
    sed -n '2,4p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 2
fi

outdir=$1
shift
offload=${STUDIO_OFFLOAD:-spectre-offload}
artifacts=${STUDIO_ARTIFACTS:-$(
    # shellcheck disable=SC1091
    . "${LIGHTNING_ENV_FILE:-$HOME/.config/remote-agent/lightning.env}" 2>/dev/null || true
    echo "${LIGHTNING_ARTIFACT_DIR:-$HOME/.local/state/remote-agent/offload-artifacts}"
)}

# The staging directory's name becomes the Studio's upload directory and the artifact's name in a
# directory other runs share, so it is unique.
name=fmc-capture-$(date -u +%Y%m%dT%H%M%SZ)-$$
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
stage=$scratch/$name
mkdir -p "$stage/i3"
rsync -a --exclude build/ --exclude .gradle/ --exclude run/ --exclude __pycache__/ \
    "$HERE/../mod" "$HERE/../tools" "$stage/i3/"

started=$SECONDS
rc=0
"$offload" --repo "$stage" \
    --setup 'bash i3/tools/studio-setup.sh' \
    --artifact "$name.tgz" \
    -- bash i3/tools/studio-capture.sh --in-studio "$name.tgz" "$@" || rc=$?

returned=$artifacts/$name.tgz
if [ -f "$returned" ]; then
    mkdir -p "$outdir"
    tar xzf "$returned" -C "$outdir" --no-overwrite-dir
    rm -f "$returned"
    echo "wall $((SECONDS - started))s (lock wait, Studio start and upload included)" \
        >> "$outdir/timings.txt"
    echo "studio-capture: $(find "$outdir" -maxdepth 1 -name '*.png' | wc -l) frame(s) in $outdir"
else
    echo "studio-capture: nothing came back from the Studio (offload rc $rc)" >&2
    ((rc)) || rc=1
fi
exit "$rc"
