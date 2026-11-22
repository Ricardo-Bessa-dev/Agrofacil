alter table app_user add column departed_at timestamptz;
alter table app_user drop constraint app_user_check;
alter table app_user add constraint ck_farmer_has_cpf
  check (role <> 'FARMER' or cpf is not null or (status = 'INACTIVE' and deleted_at is not null));
create index ix_farmer_retention on app_user (association_id, departed_at)
  where role = 'FARMER' and status = 'INACTIVE' and deleted_at is null;

create table retention_file_deletion (
  storage_key text primary key,
  queued_at timestamptz not null default now()
);

create function farmer_retention_documents(farmer_id_to_purge uuid)
returns setof generated_document language sql stable
as $$
  select d.* from generated_document d
  where d.farmer_id = farmer_id_to_purge
     or exists (select 1 from property p
                where p.farmer_id = farmer_id_to_purge and p.id = d.property_id)
$$;

create function farmer_retention_attachments(farmer_id_to_purge uuid)
returns setof attachment language sql stable
as $$
  select a.* from attachment a where
    (a.owner_type = 'RECORD' and exists (
      select 1 from management_record r where r.id = a.owner_id and r.farmer_id = farmer_id_to_purge))
    or (a.owner_type = 'PROPERTY' and exists (
      select 1 from property p where p.id = a.owner_id and p.farmer_id = farmer_id_to_purge))
    or (a.owner_type = 'CERTIFICATION' and exists (
      select 1 from certification c join property p on p.id = c.property_id
      where c.id = a.owner_id and p.farmer_id = farmer_id_to_purge))
    or (a.owner_type = 'VISIT' and exists (
      select 1 from visit v join property p on p.id = v.property_id
      where v.id = a.owner_id and p.farmer_id = farmer_id_to_purge))
    or (a.owner_type = 'DOCUMENT' and exists (
      select 1 from farmer_retention_documents(farmer_id_to_purge) d where d.id = a.owner_id))
$$;

-- Só o serviço chama esta função, depois de verificar associação, prazo e confirmação.
create function anonymize_departed_farmer(target uuid)
returns void language plpgsql
as $$
declare
  replacement text := 'Agricultor anonimizado nº ' || left(target::text, 8);
begin
  insert into retention_file_deletion (storage_key)
  select storage_key from farmer_retention_attachments(target) where storage_key is not null
  union select storage_key from farmer_retention_documents(target) where storage_key <> ''
  on conflict do nothing;

  update generated_document set status = 'CANCELLED', title = 'Documento anonimizado',
      cancel_reason = 'Retenção de dados', data_snapshot = '{}', warnings = '{}',
      signed_attachment_id = null, storage_key = '', size_bytes = 0
    where id in (select id from farmer_retention_documents(target));
  delete from attachment where id in (select id from farmer_retention_attachments(target));

  update app_user set name = replacement, cpf = null, email = null, phone = null,
      last_login_at = null, terms_version = null, terms_accepted_at = null,
      updated_at = now(), deleted_at = now(), version = version + 1
    where id = target;
  update app_user_aud set name = replacement, cpf = null, email = null, phone = null,
      terms_version = null, terms_accepted_at = null where id = target;
  update farmer set group_id = null, caf_number = null, group_joined_year = null,
      postal_code = null, uf = null, municipality_id = null, locality = null, street = null,
      number = null, complement = null, notes = null where user_id = target;
  update farmer_aud set group_id = null, caf_number = null, group_joined_year = null,
      postal_code = null, uf = null, municipality_id = null, locality = null, street = null,
      number = null, complement = null, notes = null where user_id = target;

  update property set name = 'Propriedade ' || left(id::text, 8), production_description = null,
      latitude = null, longitude = null, location_accuracy_m = null, postal_code = null, uf = null,
      municipality_id = null, locality = null, street = null, number = null, complement = null,
      access_directions = null, updated_at = now(), version = version + 1 where farmer_id = target;
  update property_aud set name = 'Propriedade ' || left(id::text, 8), production_description = null,
      latitude = null, longitude = null, location_accuracy_m = null, postal_code = null, uf = null,
      municipality_id = null, locality = null, street = null, number = null, complement = null,
      access_directions = null
    where id in (select id from property where farmer_id = target);

  update worker set name = 'Trabalhador ' || left(id::text, 8), updated_at = now(), version = version + 1
    where property_id in (select id from property where farmer_id = target);
  update worker_aud set name = 'Trabalhador ' || left(id::text, 8)
    where id in (select w.id from worker w join property p on p.id = w.property_id where p.farmer_id = target);
  update plot set name = 'Talhão ' || left(id::text, 8), notes = null,
      updated_at = now(), version = version + 1
    where property_id in (select id from property where farmer_id = target);
  update plot_aud set name = 'Talhão ' || left(id::text, 8), notes = null
    where id in (select t.id from plot t join property p on p.id = t.property_id where p.farmer_id = target);

  update management_record set notes = null, latitude = null, longitude = null,
      location_accuracy_m = null, location_captured_at = null, device_id = null,
      updated_at = now(), version = version + 1 where farmer_id = target;
  update management_record_aud set notes = null, latitude = null, longitude = null,
      location_accuracy_m = null, location_captured_at = null, device_id = null
    where id in (select id from management_record where farmer_id = target);
  update outflow set buyer_name = null where record_id in (select id from management_record where farmer_id = target);
  update outflow_aud set buyer_name = null where record_id in (select id from management_record where farmer_id = target);
  update planting set seed_supplier = null where record_id in (select id from management_record where farmer_id = target);
  update planting_aud set seed_supplier = null where record_id in (select id from management_record where farmer_id = target);
  update input_purchase set supplier = null where record_id in (select id from management_record where farmer_id = target);
  update input_purchase_aud set supplier = null where record_id in (select id from management_record where farmer_id = target);
  update correction_request set message = 'Conteúdo removido por retenção'
    where record_id in (select id from management_record where farmer_id = target);

  update certification set notes = null, status_reason = null,
      updated_at = now(), version = version + 1
    where property_id in (select id from property where farmer_id = target);
  update certification_aud set notes = null, status_reason = null
    where id in (select c.id from certification c join property p on p.id = c.property_id where p.farmer_id = target);
  update visit set improvement_points = null, recommendations = null, notes = null,
      cancel_reason = null, latitude = null, longitude = null, location_accuracy_m = null,
      brief = null, brief_updated_at = null, updated_at = now(), version = version + 1,
      answers = (select coalesce(jsonb_agg(answer - 'note'), '[]') from jsonb_array_elements(answers) answer)
    where property_id in (select id from property where farmer_id = target);
  update visit_aud set improvement_points = null, recommendations = null, notes = null,
      cancel_reason = null, latitude = null, longitude = null, location_accuracy_m = null,
      answers = (select coalesce(jsonb_agg(answer - 'note'), '[]') from jsonb_array_elements(answers) answer)
    where id in (select v.id from visit v join property p on p.id = v.property_id where p.farmer_id = target);

  delete from notification where user_id = target;
  delete from sync_mutation where user_id = target;
  delete from access_code where farmer_id = target;
  delete from refresh_token where user_id = target;
  update device set name = 'Aparelho removido', push_token = null, app_version = null where user_id = target;
end
$$;
