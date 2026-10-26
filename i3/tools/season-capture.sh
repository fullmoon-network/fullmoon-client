#!/usr/bin/env bash
# Photograph the season pass fixtures at both design sizes: every page of each standing, and the
# mission widget on the HUD after the screen closes.
#
#   tools/season-capture.sh OUTDIR WORLD
#
# WORLD is any world directory; it is copied, never opened in place. The fixtures draw reward and
# mission items, which need a world to build them in, so the screen opens once the player has
# joined. Frames land in OUTDIR/<gui size>/<standing>/, each run's log beside them.
#
# The widget's fixture loops every six seconds — a finished stage for two, then a mission that just
# moved for three — so the HUD is photographed three times, two seconds apart. OUTDIR/DONE is
# written last, so a watcher can tell a finished run from one still going.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)

(($# == 2)) || { sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2; }
outdir=$1
world=$2
[ -f "$world/level.dat" ] || { echo "season-capture: no level.dat in '$world'" >&2; exit 2; }

for geometry in 1920x1080 1280x720; do
    width=${geometry%x*} height=${geometry#*x}
    gui="$((width / 2))x$((height / 2))"
    for standing in season-plus season-free season-claim; do
        shots=(
            "track:wait=2"
            "track-next:Right"
            "missions:Control_L+Tab"
            "missions-row:Down,Down"
            "plus:Control_L+Tab"
        )
        if [ "$standing" = season-plus ]; then
            shots+=("hud-1:Escape,wait=1" "hud-2:wait=2" "hud-3:wait=2")
        fi
        python3 "$HERE/capture.py" "$outdir/$gui/$standing" --geometry "$geometry" --scale 2 \
            --world "$world" --dev-screen "$standing" "${shots[@]}"
    done
done
date -Is > "$outdir/DONE"
