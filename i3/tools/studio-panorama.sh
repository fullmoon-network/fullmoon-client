#!/usr/bin/env bash
# Take the title-screen panorama from a copy of a world, in the Lightning Studio.
#
#   tools/studio-panorama.sh OUTDIR --world DIR [--geometry WxH] [--distance CHUNKS] STEP...
#
# A STEP is one line of tools/panorama/PanoramaGrab.java's script, quoted:
#   'panorama NAME X Y Z YAW'        the six faces, face 0 looking along YAW, into OUTDIR/NAME/
#   'view NAME X Y Z YAW PITCH'      one frame of the window, as OUTDIR/view-NAME.png
#   'command TEXT'  'gamma VALUE'    a server command run as the console, the brightness option
# X Y Z are the feet of the camera; the eye is 1.62 above. YAW is Minecraft's: 180 looks north.
#
# The world is copied, never opened in place: the Studio gets a copy of DIR, loads it by quick play
# singleplayer, and the copy is thrown away with the upload. The client runs out of a throwaway copy
# of i3/mod that has PanoramaGrab compiled in and registered, so nothing here reaches the mod's jar.
# What comes back into OUTDIR: the frames, run.log, build.log, glinfo.txt, memory.txt and
# timings.txt.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
# Midnight on day 0. Time is a world clock in 26.1 and `time set` sets its total ticks, and the
# moon-phase timeline is eight days of 24000 ticks starting at full, so day 0 is a full moon; the
# clock is then paused so the moon does not move while chunks build.
AMBIENCE=(
    'command gamemode spectator @a'
    'command time set 18000'
    'command time pause'
    'command weather clear'
)

in_studio() {
    local tarball=$1 geometry=$2 distance=$3
    local state=/tmp/fmc-panorama
    local work=$state/tree out=$state/out run
    local started=$SECONDS mark rc=0
    # shellcheck disable=SC1091
    . /tmp/fmc-capture/env
    export JAVA_HOME
    export GRADLE_OPTS="-Dorg.gradle.daemon=false"
    # gradle.properties gives Gradle 3 GB for builds; launching a client needs a fraction of that,
    # and the client beside it is what needs the memory of a shared Studio.
    local lean=-Dorg.gradle.jvmargs=-Xmx1536m

    pkill -f "^Xvfb :(9|19) " || true
    pkill -f -- "$work/i3/mod/" || true
    rm -rf "$out"
    mkdir -p "$work" "$out"
    rsync -a --delete --exclude build/ --exclude .gradle/ --exclude run/ ./ "$work/"
    chmod +x "$work/i3/mod/gradlew"

    local mod=$work/i3/mod
    mkdir -p "$mod/src/main/java/dev/fullmoon/tools/panorama"
    cp "$work/i3/tools/panorama/PanoramaGrab.java" "$mod/src/main/java/dev/fullmoon/tools/panorama/"
    /usr/bin/python3 - "$mod/src/main/resources/fabric.mod.json" <<'EOF'
import json, sys
path = sys.argv[1]
manifest = json.load(open(path))
manifest["entrypoints"]["client"].append("dev.fullmoon.tools.panorama.PanoramaGrab")
json.dump(manifest, open(path, "w"), indent=2, ensure_ascii=False)
EOF
    cat >> "$mod/build.gradle.kts" <<'KTS'

loom {
    runs.named("client") {
        programArgs("--quickPlaySingleplayer", "lobby")
        vmArgs("-Xmx2G")
    }
}
KTS

    run=$mod/run
    rm -rf "$run"
    mkdir -p "$run/saves"
    cp -r "$work/world" "$run/saves/lobby"
    cp "$work/steps.txt" "$run/panorama-steps.txt"
    # A first launch opens on the accessibility onboarding screen, and a window without focus pauses
    # a singleplayer world, which stops the section compiler along with everything else.
    printf '%s\n' onboardAccessibility:false pauseOnLostFocus:false tutorialStep:none \
        "renderDistance:$distance" "simulationDistance:5" chunkSectionFadeInTime:0.0 \
        soundCategory_master:0.0 > "$run/options.txt"

    Xvfb :19 -screen 0 64x64x24 +extension GLX -nolisten tcp >/dev/null 2>&1 &
    local probe=$!
    sleep 1
    DISPLAY=:19 glxinfo -B > "$out/glinfo.txt" 2>&1 || true
    kill "$probe" 2>/dev/null || true

    mark=$SECONDS
    (cd "$mod" && ./gradlew -p . "$lean" classes configureClientLaunch --console=plain) \
        > "$out/build.log" 2>&1 || rc=$?
    echo "build $((SECONDS - mark))s rc=$rc" >> "$out/timings.txt"
    tail -n 3 "$out/build.log"

    if ((rc == 0)); then
        mark=$SECONDS
        Xvfb :9 -screen 0 "${geometry}x24" +extension GLX +extension RANDR -nolisten tcp \
            >/dev/null 2>&1 &
        local xvfb=$!
        sleep 1.5
        local width=${geometry%x*} height=${geometry#*x}
        (cd "$mod" && DISPLAY=:9 exec setsid ./gradlew -p . "$lean" runClient --console=plain \
            "-Pclient_width=$width" "-Pclient_height=$height") > "$out/run.log" 2>&1 &
        local client=$!
        # Every step logs, and a settle that never comes is capped at three minutes a direction,
        # so a run that is still going after this long is stuck on a screen the script cannot see.
        # The Studio can be taken away mid-run (the session cap, a restart), and /tmp goes with it,
        # so the log and the memory curve are mirrored into the persistent home as the run goes.
        local deadline=$((SECONDS + 2400)) mirror=$HOME/fmc-panorama-last
        rm -rf "$mirror" && mkdir -p "$mirror"
        while kill -0 "$client" 2>/dev/null && ((SECONDS < deadline)); do
            grep -q 'panorama: done' "$out/run.log" && break
            if ((SECONDS % 30 < 5)); then
                free -m | awk -v t=$((SECONDS - mark)) \
                    '/^Mem:/ {print t "s used " $3 " MB of " $2}' >> "$out/memory.txt"
                cp "$out/run.log" "$out/memory.txt" "$mirror/" 2>/dev/null || true
            fi
            sleep 5
        done
        grep -q 'panorama: done' "$out/run.log" || rc=1
        sleep 10
        pkill -f -- "$mod/" || true
        kill "$xvfb" 2>/dev/null || true
        echo "client $((SECONDS - mark))s rc=$rc" >> "$out/timings.txt"
        grep 'Fullmoon/Panorama' "$out/run.log" | tail -n 40 || true
        [ -d "$run/screenshots" ] && cp -r "$run/screenshots/." "$out/"
        [ -d "$run/crash-reports" ] && cp -r "$run/crash-reports" "$out/"
    fi
    echo "studio $((SECONDS - started))s" >> "$out/timings.txt"
    tar czf "$tarball" -C "$out" .
    case $rc in 5|6|255) rc=1 ;; esac
    return "$rc"
}

if [ "${1:-}" = "--in-studio" ]; then
    shift
    in_studio "$@"
    exit
fi

usage() {
    sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 2
}

(($# >= 1)) && [[ $1 != -* ]] || usage
outdir=$1
shift
world="" geometry=1280x720 distance=16
while (($#)); do
    case $1 in
        --world) world=$2; shift 2 ;;
        --geometry) geometry=$2; shift 2 ;;
        --distance) distance=$2; shift 2 ;;
        -*) usage ;;
        *) break ;;
    esac
done
[ -f "$world/level.dat" ] || { echo "studio-panorama: no level.dat in '$world'" >&2; exit 2; }
(($#)) || usage

offload=${STUDIO_OFFLOAD:-spectre-offload}
artifacts=${STUDIO_ARTIFACTS:-$(
    # shellcheck disable=SC1091
    . "${LIGHTNING_ENV_FILE:-$HOME/.config/remote-agent/lightning.env}" 2>/dev/null || true
    echo "${LIGHTNING_ARTIFACT_DIR:-$HOME/.local/state/remote-agent/offload-artifacts}"
)}

name=fmc-panorama-$(date -u +%Y%m%dT%H%M%SZ)-$$
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
stage=$scratch/$name
mkdir -p "$stage/i3"
rsync -a --exclude build/ --exclude .gradle/ --exclude run/ --exclude __pycache__/ \
    "$HERE/../mod" "$HERE/../tools" "$stage/i3/"
# A live server holds session.lock and rewrites region files; the copy is taken as it stands.
rsync -a --exclude session.lock "$world/" "$stage/world/"
# Paper runs each dimension as a world of its own and keeps world_gen_settings.dat inside each one;
# singleplayer reads a single copy at the world root and refuses to load without it.
settings=data/minecraft/world_gen_settings.dat
overworld=$world/dimensions/minecraft/overworld/$settings
if [ ! -f "$stage/world/$settings" ] && [ -f "$overworld" ]; then
    mkdir -p "$stage/world/data/minecraft"
    cp "$overworld" "$stage/world/$settings"
fi
printf '%s\n' "${AMBIENCE[@]}" "$@" > "$stage/steps.txt"

started=$SECONDS
rc=0
"$offload" --repo "$stage" \
    --setup 'bash i3/tools/studio-setup.sh' \
    --artifact "$name.tgz" \
    -- bash i3/tools/studio-panorama.sh --in-studio "$name.tgz" "$geometry" "$distance" || rc=$?

returned=$artifacts/$name.tgz
if [ -f "$returned" ]; then
    mkdir -p "$outdir"
    tar xzf "$returned" -C "$outdir" --no-overwrite-dir
    rm -f "$returned"
    echo "wall $((SECONDS - started))s (lock wait, Studio start and upload included)" \
        >> "$outdir/timings.txt"
    # The grab writes RGBA with every alpha at 255 and light compression, and the faces ship in the
    # jar, so they are rewritten as RGB at the strongest zlib setting. The pixels do not change.
    python3 - "$outdir" <<'PY' || echo "studio-panorama: faces left as grabbed (no Pillow?)" >&2
import pathlib, sys
from PIL import Image
for face in sorted(pathlib.Path(sys.argv[1]).glob("*/panorama_[0-5].png")):
    image = Image.open(face)
    assert image.mode != "RGBA" or image.getchannel("A").getextrema() == (255, 255), face
    image.convert("RGB").save(face, optimize=True)
PY
    echo "studio-panorama: $(find "$outdir" -name '*.png' | wc -l) frame(s) in $outdir"
else
    echo "studio-panorama: nothing came back from the Studio (offload rc $rc)" >&2
    ((rc)) || rc=1
fi
exit "$rc"
