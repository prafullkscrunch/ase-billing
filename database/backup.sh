#!/usr/bin/env bash
# Nightly backup. Keeps 30 days. Add to cron:
#   0 22 * * * /path/to/ase-billing/database/backup.sh
set -euo pipefail

DB_NAME="${DB_NAME:-ase_billing}"
DB_USER="${DB_USER:-ase}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/ase-backups}"
KEEP_DAYS=30

mkdir -p "$BACKUP_DIR"
STAMP=$(date +%F_%H%M)
OUT="$BACKUP_DIR/${DB_NAME}_${STAMP}.sql.gz"

mysqldump --single-transaction --routines --triggers \
  -u "$DB_USER" -p"${DB_PASSWORD:?set DB_PASSWORD}" "$DB_NAME" | gzip > "$OUT"

# Fail loudly if the dump came out suspiciously small.
if [ "$(stat -c%s "$OUT")" -lt 10240 ]; then
  echo "Backup $OUT is under 10KB - check the database" >&2
  exit 1
fi

find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +$KEEP_DAYS -delete
echo "Backed up to $OUT"
