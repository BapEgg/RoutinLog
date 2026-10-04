#!/usr/bin/env bash
set -euo pipefail
umask 077
# A local staging copy is not an off-site backup. Encrypt and copy to your private backup service.
: "${ROUTINLOG_ENV_FILE:?Specify the private production env file}"
: "${BACKUP_DIRECTORY:?Specify a private backup directory}"
root="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$BACKUP_DIRECTORY"
target="$BACKUP_DIRECTORY/routinlog-$(date -u +%Y%m%dT%H%M%SZ).dump"
docker compose --env-file "$ROUTINLOG_ENV_FILE" -f "$root/deploy/compose.production.yaml" exec -T postgres pg_dump -U routinlog -d routinlog -Fc > "$target.part"
test -s "$target.part"
mv -- "$target.part" "$target"
sha256sum "$target" > "$target.sha256"
printf 'Backup created; verify restoration in an isolated database before relying on it.\n'
