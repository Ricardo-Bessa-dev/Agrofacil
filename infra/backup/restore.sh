#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ $# != 3 || ! -d "$1" || ! "$2" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ || -e "$3" || -L "$3" ]]; then
  printf 'Uso: %s DIRETORIO_DO_BACKUP NOME_DE_UM_NOVO_BANCO NOVO_DIRETORIO_DE_ANEXOS\n' "$0" >&2
  exit 2
fi

backup_dir=$(realpath -- "$1")
(
  cd "$backup_dir"
  sha256sum --strict --check SHA256SUMS
)
pg_restore --list < "$backup_dir/database.dump" >/dev/null
tar -tzf "$backup_dir/files.tar.gz" >/dev/null

mkdir -- "$3"
if ! createdb "$2"; then
  rmdir -- "$3"
  exit 1
fi

if ! pg_restore --exit-on-error --single-transaction --no-owner --no-privileges --dbname="$2" < "$backup_dir/database.dump"; then
  printf 'Restauração do banco falhou. O banco novo foi reservado para diagnóstico.\n' >&2
  exit 1
fi
tar --no-same-owner --no-same-permissions -xzf "$backup_dir/files.tar.gz" -C "$3"
printf 'Banco restaurado em %s. Anexos extraídos em %s.\n' "$2" "$3"
