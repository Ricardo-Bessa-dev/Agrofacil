#!/usr/bin/env bash
# Usa PGHOST, PGPORT, PGUSER e PGPASSFILE para acessar o PostgreSQL.
set -euo pipefail
umask 077

if [[ $# != 3 || ! -d "$2" ]]; then
  printf 'Uso: %s BANCO DIRETORIO_DE_ANEXOS DESTINO_DOS_BACKUPS\n' "$0" >&2
  exit 2
fi

files_dir=$(realpath -- "$2")
backup_root=$(realpath -m -- "$3")
if [[ "$backup_root" == "$files_dir" || "$backup_root" == "$files_dir/"* ]]; then
  printf 'O destino do backup deve ficar fora do diretório de anexos.\n' >&2
  exit 2
fi
mkdir -p -- "$backup_root"
stamp=$(date -u '+%Y%m%dT%H%M%SZ')
destination="$backup_root/agrofacil-$stamp"
if [[ -e "$destination" ]]; then
  printf 'Já existe um backup para este horário.\n' >&2
  exit 1
fi
working=$(mktemp -d "$backup_root/.agrofacil-$stamp.XXXXXX")
trap 'rm -r -- "$working"' EXIT

pg_dump --format=custom --no-owner --no-privileges --dbname="$1" > "$working/database.dump"
tar -C "$files_dir" -czf "$working/files.tar.gz" .
(
  cd "$working"
  sha256sum database.dump files.tar.gz > SHA256SUMS
)
mv -T -- "$working" "$destination"
trap - EXIT

printf '%s\n' "$destination"
