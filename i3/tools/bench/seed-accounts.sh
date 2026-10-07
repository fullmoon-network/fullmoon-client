#!/usr/bin/env bash
# seed-accounts.sh: gives the replica DB the bench accounts, one per client runner, all linked and
# with the tutorial skipped, so every runner sees the same HUD and no welcome tutorial. Runs ON the
# replica runner (restore.sh --keep) and writes only to its own throwaway PostgreSQL.
set -euo pipefail
DB="${DB_NAME:-coinbridge}"
if [ -d /opt/mc-network/servers ] && [ "$DB" = coinbridge ]; then
  echo "refusing to seed bench accounts into production's database on the production host" >&2; exit 3
fi
n=0
for name in "$@"; do
  n=$((n + 1))
  uuid=$(python3 - "$name" <<'PY'
import hashlib, sys, uuid
d = bytearray(hashlib.md5(f'OfflinePlayer:{sys.argv[1]}'.encode()).digest())
d[6] = d[6] & 0x0F | 0x30; d[8] = d[8] & 0x3F | 0x80
print(uuid.UUID(bytes=bytes(d)))
PY
)
  sudo -u postgres psql -X -q -v ON_ERROR_STOP=1 -d "$DB" <<SQL
INSERT INTO accounts (discord_id, mc_uuid, mc_username, linked_at)
  VALUES (9000000000000001$((10 + n)), '$uuid', '$name', now())
  ON CONFLICT (mc_uuid) DO UPDATE SET discord_id = EXCLUDED.discord_id, linked_at = EXCLUDED.linked_at;
INSERT INTO balances (account_id) SELECT id FROM accounts WHERE mc_uuid = '$uuid' ON CONFLICT (account_id) DO NOTHING;
INSERT INTO tutorial_progress (mc_uuid, skipped_at) VALUES ('$uuid', now())
  ON CONFLICT (mc_uuid) DO UPDATE SET skipped_at = now();
SQL
  echo "seeded $name -> $uuid"
done
