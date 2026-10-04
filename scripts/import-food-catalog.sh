#!/usr/bin/env bash
set -euo pipefail
: "${ROUTINLOG_ENV_FILE:?Specify the private production env file}"
: "${CATALOG_FILE:?Specify the reviewed converter JSONL output}"
test -f "$CATALOG_FILE"
root="$(cd "$(dirname "$0")/.." && pwd)"
catalog="$(cd "$(dirname "$CATALOG_FILE")" && pwd)/$(basename "$CATALOG_FILE")"
# Uses the validated transactional importer. Existing personal foods and historical meals remain snapshots.
docker compose --env-file "$ROUTINLOG_ENV_FILE" -f "$root/deploy/compose.production.yaml" run --rm --no-deps \
  -v "$catalog:/catalog/input.jsonl:ro" server \
  --routinlog.food-catalog.import-file=/catalog/input.jsonl --routinlog.food-catalog.exit-after-import=true --server.port=0
