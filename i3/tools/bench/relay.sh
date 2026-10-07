#!/usr/bin/env bash
# relay.sh up|down|check: reaches the replica from client runners, no public ports.
#
#   replica 127.0.0.1:{48291,25575,25576} <-ssh -L- this PC 127.0.0.1:{48391,48392,48393} <-ssh -R- each client runner
#
# The burst security group admits ssh from the operator's /32 only, so runners cannot reach each other;
# this machine carries the hop. 48291 is Velocity (the game), 25575/25576 the lobby/survival RCON, 48394 flightd.py.
#   REPLICA_IP=<ip> CLIENT_IPS="<ip> <ip>" ./relay.sh up
set -uo pipefail
RUN_USER="${RUN_USER:-ec2-user}"
ST="${RELAY_STATE:-$HOME/.cache/bench-relay}"; mkdir -p "$ST"
KA=(-o ServerAliveInterval=15 -o ServerAliveCountMax=3 -o ExitOnForwardFailure=yes -o BatchMode=yes)
PORTS=(48291:48391 25575:48392 25576:48393 48394:48394)   # remote:local
alive() { [ -f "$ST/$1.pid" ] && kill -0 "$(cat "$ST/$1.pid")" 2>/dev/null; }
hop() { # name, host, forward flags...
  local name=$1 host=$2; shift 2
  # shellcheck disable=SC2046
  setsid ssh -N "${KA[@]}" $(aws-burst sshopts) "$@" "$RUN_USER@$host" >"$ST/$name.log" 2>&1 </dev/null &
  echo $! >"$ST/$name.pid"
}
case "${1:-}" in
up)
  : "${REPLICA_IP:?set REPLICA_IP}"; : "${CLIENT_IPS:?set CLIENT_IPS}"
  L=(); for p in "${PORTS[@]}"; do L+=(-L "127.0.0.1:${p#*:}:127.0.0.1:${p%%:*}"); done
  alive replica || hop replica "$REPLICA_IP" "${L[@]}"
  sleep 3
  for ip in $CLIENT_IPS; do
    R=(); for p in "${PORTS[@]}"; do R+=(-R "127.0.0.1:${p%%:*}:127.0.0.1:${p#*:}"); done
    alive "client-$ip" || hop "client-$ip" "$ip" "${R[@]}"
  done
  sleep 3
  echo "$CLIENT_IPS" >"$ST/clients"; bash "${BASH_SOURCE[0]}" check ;;
down)
  for f in "$ST"/*.pid; do [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null; rm -f "$f"; done; echo "relay down" ;;
check)
  rc=0
  for f in "$ST"/*.pid; do
    n=$(basename "$f" .pid)
    if alive "$n"; then echo "ok   $n hop"; else echo "FAIL $n hop dead ($(tail -n1 "$ST/$n.log" 2>/dev/null))"; rc=1; fi
  done
  for ip in ${CLIENT_IPS:-$(cat "$ST/clients" 2>/dev/null)}; do
    for p in "${PORTS[@]}"; do
      # shellcheck disable=SC2046
      if ssh -o BatchMode=yes $(aws-burst sshopts) "$RUN_USER@$ip" "timeout 5 bash -c 'exec 3<>/dev/tcp/127.0.0.1/${p%%:*}'" 2>/dev/null; then
        echo "ok   $ip 127.0.0.1:${p%%:*} accepts"; else echo "FAIL $ip 127.0.0.1:${p%%:*}"; rc=1; fi
    done
  done
  exit $rc ;;
*) echo "usage: relay.sh up|down|check" >&2; exit 2 ;;
esac
