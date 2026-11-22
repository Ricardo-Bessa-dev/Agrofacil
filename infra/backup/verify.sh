#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 2 || ! -d "$2" ]]; then
  printf 'Uso: %s BANCO_RESTAURADO DIRETORIO_DE_ANEXOS_RESTAURADOS\n' "$0" >&2
  exit 2
fi

# O Flyway e os anexos/documentos são verificados no banco restaurado.
psql -X --dbname="$1" --set=ON_ERROR_STOP=1 -Atc 'select version from flyway_schema_history where success order by installed_rank desc limit 1'
keys=$(psql -X --dbname="$1" --set=ON_ERROR_STOP=1 -Atc "
  select storage_key || ' ' || trim(sha256) from attachment
    where uploaded_at is not null and deleted_at is null
      and storage_key is not null and sha256 is not null
  union all
  select storage_key || ' ' || trim(sha256) from generated_document where storage_key <> ''
")
count=0
while read -r key expected; do
  [[ -n "$key" ]] || continue
  if [[ ! -f "$2/$key" ]]; then
    printf 'Arquivo ausente no backup: %s\n' "$key" >&2
    exit 1
  fi
  actual=$(sha256sum < "$2/$key")
  if [[ "${actual%% *}" != "$expected" ]]; then
    printf 'Hash diferente do banco: %s\n' "$key" >&2
    exit 1
  fi
  count=$((count + 1))
done <<< "$keys"
printf '%s arquivo(s) conferido(s).\n' "$count"
