#!/usr/bin/env bash
set -euo pipefail
files=$1
temp=$2
compose="$(realpath infra/docker-compose.dev.yml)"
mkdir -p "$temp/agrofacil-pg-tools"
for command in pg_dump pg_restore createdb psql; do
  cat > "$temp/agrofacil-pg-tools/$command" <<WRAPPER
#!/usr/bin/env bash
exec docker compose -f "$compose" exec -T postgres $command -U agrofacil "\$@"
WRAPPER
  chmod +x "$temp/agrofacil-pg-tools/$command"
done
export PATH="$temp/agrofacil-pg-tools:$PATH"

backup=$(infra/backup/backup.sh agrofacil "$files" "$temp/agrofacil-backup")
restored=agrofacil_restaurado
restored_files="$temp/agrofacil-files-restored"
infra/backup/restore.sh "$backup" "$restored" "$restored_files"
infra/backup/verify.sh "$restored" "$restored_files"

count=$(psql -X -At --dbname="$restored" --set=ON_ERROR_STOP=1 -c "
  select count(*) from management_record r
  join outflow o on o.record_id = r.id
  join attachment a on a.owner_type = 'RECORD' and a.owner_id = r.id
  where r.notes = 'Venda registrada sem internet' and a.uploaded_at is not null
    and o.quantity_unit = 'KILOGRAM'
")
test "$count" = 1
