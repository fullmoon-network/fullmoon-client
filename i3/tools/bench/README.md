# Client benchmark harness

A repeatable, scripted measurement of the Fullmoon client (Fabric mod for Minecraft 26.1.2 plus the
mods and JVM flags the launcher ships), so a change to the mod, the launcher's mod catalog or its JVM
defaults is decided by numbers. It does not build anything and it is not part of CI.

**It never runs on a workstation.** A client under software GL is a build-class load; everything here
runs on a disposable `aws-burst` runner (2 vCPU) against a replica of the production servers
restored from backups. Nothing in it contacts production.

## What it measures

| Scenario | Render distance | What happens | Window |
|---|---|---|---|
| `title` | 6 | process start to the title screen, no server | n/a (times) |
| `idle` | 6 | lobby spawn with the HUD and sidebar; then the map; then the native `/warp` menu | 120 s, 60 s, 60 s |
| `fly` | 12 | a scripted spectator flight over the lobby, then `/server survival` and a flight over Taecho and the terrain around it with a fixed NoAI herd | 120 s, 120 s |
| `mem` | 6 | `-XX:NativeMemoryTracking=summary`, 60 s idle, summary and diff (kept apart so NMT cannot skew timing) | 60 s |
| `startup` | 6 | JFR from the first instruction with traced font and entrypoint methods | start to world |
| `parity-title` | 6 | title with the sidebar fixture and the dev pages, screenshots only | n/a |
| `aot-train` | 6 | one launch that writes the JDK 25 AOT cache (`-XX:AOTCacheOutput`) | n/a |
| `dfps`, `smoke` | 6 | focused vs unfocused windows (Dynamic FPS), and a join-and-idle functional check | 30 s / 40 s |

Movement is deterministic: the driver sends `tp <player> x y z yaw pitch` over RCON at 10 Hz along the
polylines in `flights.json` at 10 blocks/s (`prep-world.py` first puts the same silent herd along the
야생 path). Fixed state before every window: noon, clear weather, time and weather frozen, `maxFps` 260,
1280x720 Xvfb, Mesa llvmpipe.

Per window (one JFR recording each):

* **Frame time**: `jdk.MethodTrace` (JDK 25) on `Minecraft.runTick`, one event per frame. Frame time is
  the start-to-start interval of consecutive `runTick` calls, which is what an fps counter shows. avg, p50,
  p95, p99, p99.9, max and the 1%-low fps (mean fps of the slowest 1% of frames). `GameRenderer.render`
  duration is recorded from the same source as "render ms".
* **Render-thread allocation rate**: `jdk.ThreadAllocationStatistics`, delta over the window.
* **GC**: count, total pause and longest pause from `jdk.GarbageCollection`.
* **Live heap**: `GC.heap_info` after a forced `GC.run` at the end of the run.
* **RSS / PSS / anonymous memory**: `/proc/<pid>` every 5 s and at the end. **Native memory**:
  `VM.native_memory summary` and `summary.diff` in the `mem` scenario only.
* **CPU steal** from `/proc/stat` per window, so a noisy host shows up as a number rather than a mystery.
* **Cold start**: polled over the MCP port every 100 ms. `title_s` is the first `TitleScreen` subclass,
  `in_game_s` the login, `world_visible_s` the first poll after the loading screen is gone. All are
  seconds from `Popen`. The page cache is warm (the engine and jars were read by an earlier launch).

## Run it

```sh
# 1. a replica of production, from backups, never touching the box (see servers-network/scripts/replica)
PROD_SSH=unused@invalid.example ./restore.sh --out ./out --keep --no-prod-query --ttl 360 \
  --env SKIP_GITEA=1 --env SKIP_PLATFORM=1
# the lobby and survival view-distance must be 12 or the 12-chunk flights are capped by the server
# (the replica template ships 10 and production runs 12 / 6)

# 2. one client runner per repeat, same instance type, ideally the same region as this machine
BURST_REGION=ap-northeast-2 aws-burst up --type m7i-flex.large --ttl 300 --idle 0 --name bench-client1
rsync -a --exclude tests i3/tools/bench/ ec2-user@<ip>:bench/ ; rsync -a <jars>/ ec2-user@<ip>:bench/jars/
aws-burst ssh <id> 'bash bench/setup-runner.sh'

# 3. accounts (one per runner), relay and RCON password (never printed)
aws-burst ssh <replica-id> 'bash ~/seed-accounts.sh BenchA BenchB'
REPLICA_IP=<ip> CLIENT_IPS="<ip1> <ip2>" ./relay.sh up
aws-burst ssh <replica-id> 'grep ^RCON_PASSWORD ~/replica/.secrets' | ssh ec2-user@<client> 'umask 077; cat > bench/rcon.env'
aws-burst ssh <client-id> 'cd bench; set -a; . ./rcon.env; set +a; venv/bin/python prep-world.py'

# 4. a plan is one "<config> <scenario> <rep>" per line; the queue resumes where it stopped
aws-burst ssh <client-id> 'cd bench; BENCH_PLAYER=BenchA setsid nohup venv/bin/python bench.py queue plan.txt --runner r1 </dev/null >queue.log 2>&1 &'

# 5. pull the runs home and compare
rsync -a ec2-user@<client>:bench/runs/ runs/
python3 i3/tools/bench/summarize.py runs --window fly-survival --markdown
python3 i3/tools/bench/summarize.py runs --startup ; python3 i3/tools/bench/summarize.py runs --memory
python3 i3/tools/bench/parity.py runs if ec mc bo
aws-burst down <every id you started>; ./relay.sh down
```

`configs.json` defines a config as the baseline mod set and JVM flags plus a delta; add a config there.
Jars are checked against `jars.sha256` and, for the candidates, against the sha512 Modrinth publishes.
Raw JFR files are large and stay out of git; keep them with the evidence.

## Reading the numbers

Each cell is the mean of the repeats, `±` is half the range over the mean, and `(+x%)` is the change
against the baseline's mean. A change smaller than the baseline's own `±` is noise. Run the repeats of a
config on different runners and compare within a runner.

### What does not transfer to a real GPU

Under llvmpipe the rasteriser is the CPU: the game runs at 4 to 8 fps and a frame is mostly fragment
work on the same two cores that run the render thread. So:

* Frame-time deltas from anything that changes **GPU-side work** (fewer draw calls, fewer triangles,
  culling) are real but compressed or inflated by a software rasteriser, and CPU-bound frames on a real
  machine will not look like this. Treat the *sign and mechanism* as evidence, not the percentage.
* Anything that trims **Java heap, allocation, GC, native memory and start-up** transfers well: those
  costs are the JVM's, not the rasteriser's.
* Present/vsync, driver overhead, buffer upload bandwidth and GPU memory are not exercised at all.
  ImmediatelyFast (batched immediate-mode drawing) and Sodium-style culling live there.
