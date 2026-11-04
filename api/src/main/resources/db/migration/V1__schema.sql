-- V1__schema.sql: esquema inicial do Agrofácil (doc 2, Parte 2).

-- Busca textual que ignora acentos (doc 2 §1.9). unaccent() não é imutável; o invólucro permite usá-la em índices.
create extension if not exists unaccent;
create function f_unaccent(text) returns text language sql immutable parallel safe strict as
$$ select public.unaccent('public.unaccent'::regdictionary, $1) $$;

-- Contador global de mudanças usado pelo pull da sincronização (doc 1, Parte 4).
create sequence sync_seq;
create sequence record_number_seq;

-- Atribui sync_seq a cada escrita. O lock consultivo serializa as transações que
-- escrevem dados sincronizáveis, para que a ordem de sync_seq seja igual à ordem de commit (ADR-09).
create function set_sync_seq() returns trigger language plpgsql as $$
begin
  perform pg_advisory_xact_lock(724001);
  new.sync_seq := nextval('sync_seq');
  return new;
end $$;

create table municipality (
  id   integer primary key,          -- código IBGE (7 dígitos)
  name text not null,
  uf   char(2) not null
);

create table association (
  id                     uuid primary key,
  name                   text not null,
  cnpj                   char(14),
  email                  text,
  phone                  text,
  whatsapp               text,
  opac_name              text,
  tarefa_m2              numeric(10,2) not null default 4356,
  alert_days             integer[] not null default '{60,30,15}',
  access_code_ttl_hours  integer not null default 72,
  retention_years        integer not null default 5,
  visit_window_days      integer not null default 90,
  peer_visits_per_year   integer not null default 2,
  visitors_per_visit     integer not null default 2,
  visit_reminder_days    integer not null default 1,
  verification_same_group_allowed boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table spg_group (
  id              uuid primary key,
  association_id  uuid not null references association(id),
  name            text not null,
  municipality_id integer references municipality(id),
  notes           text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table app_user (
  id                uuid primary key,
  association_id    uuid not null references association(id),
  role              text not null check (role in ('FARMER','COORDINATOR')),
  name              text not null,
  cpf               char(11),
  email             text,
  phone             text,
  password_hash     text,
  status            text not null default 'ACTIVE' check (status in ('ACTIVE','INACTIVE')),
  terms_accepted_at timestamptz,
  terms_version     text,
  last_login_at     timestamptz,
  sync_reset_at     timestamptz,           -- pull posterior a esta data recebe reset (doc 1 §4.8)
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0,
  check (role <> 'FARMER' or cpf is not null),
  check (role <> 'COORDINATOR' or email is not null)
);
create unique index ux_app_user_cpf   on app_user (cpf)          where deleted_at is null and cpf is not null;
create unique index ux_app_user_email on app_user (lower(email)) where deleted_at is null and email is not null;
create index ix_app_user_assoc_role on app_user (association_id, role);

create table farmer (
  user_id           uuid primary key references app_user(id),
  group_id          uuid references spg_group(id),
  caf_number        text,
  group_joined_year integer,
  postal_code       char(8),
  uf                char(2),
  municipality_id   integer references municipality(id),
  locality          text,
  street            text,
  number            text,
  complement        text,
  notes             text
);

create table property (
  id                     uuid primary key,
  association_id         uuid not null references association(id),
  farmer_id              uuid not null references app_user(id),
  name                   text not null,
  total_area             numeric(12,3),
  total_area_unit        text,
  total_area_m2          numeric(14,2),
  production_description text,
  latitude               numeric(9,6),
  longitude              numeric(9,6),
  location_accuracy_m    numeric(8,1),
  postal_code            char(8),
  uf                     char(2),
  municipality_id        integer references municipality(id),
  locality               text,
  street                 text,
  number                 text,
  complement             text,
  access_directions      text,
  created_by             uuid not null references app_user(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);
create index ix_property_farmer on property (farmer_id) where deleted_at is null;

create table plot (
  id                 uuid primary key,
  property_id        uuid not null references property(id),
  name               text not null,
  area               numeric(12,3),
  area_unit          text,
  area_m2            numeric(14,2),
  status             text not null default 'NOT_DEFINED'
                     check (status in ('ORGANIC','IN_CONVERSION','NON_ORGANIC','NOT_DEFINED')),
  conversion_ends_on date,
  notes              text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table worker (
  id           uuid primary key,
  property_id  uuid not null references property(id),
  name         text not null,
  relationship text not null check (relationship in ('FAMILY','EMPLOYEE','PARTNER','OTHER')),
  active       boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table certification (
  id            uuid primary key,
  property_id   uuid not null references property(id),
  number        text,
  issued_on     date not null,
  expires_on    date not null,
  manual_status text not null default 'ACTIVE' check (manual_status in ('ACTIVE','SUSPENDED','CANCELLED')),
  status_reason text,
  scope         text,
  previous_id   uuid references certification(id),
  notes         text,
  created_by    uuid not null references app_user(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0,
  check (expires_on > issued_on)
);
create index ix_certification_property on certification (property_id, issued_on desc);

create table crop_category (
  id   uuid primary key,
  name text not null,
  icon text
);

create table crop (
  id             uuid primary key,
  association_id uuid references association(id),
  category_id    uuid references crop_category(id),
  name           text not null,
  icon           text,
  active         boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table certification_product (
  certification_id uuid not null references certification(id),
  crop_id          uuid references crop(id),
  product_name     text,
  check (crop_id is not null or product_name is not null)
);

create table certification_worker (
  certification_id uuid not null references certification(id),
  worker_id        uuid not null references worker(id),
  primary key (certification_id, worker_id)
);

create table certification_alert (
  certification_id uuid not null references certification(id),
  days_before      integer not null,
  sent_at          timestamptz not null default now(),
  primary key (certification_id, days_before)
);

create table farm_input (
  id                   uuid primary key,
  association_id       uuid references association(id),
  name                 text not null,
  input_type           text not null check (input_type in
                        ('FERTILIZER','SOIL_CORRECTIVE','BIOFERTILIZER','PEST_DISEASE_CONTROL','INOCULANT','OTHER')),
  regulatory_reference text,
  needs_authorization  boolean not null default false,
  home_made            boolean not null default false,
  active               boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table measurement_unit (
  code           text primary key,
  name_singular  text not null,
  name_plural    text not null,
  kind           text not null check (kind in ('AREA','MASS','VOLUME','COUNT','PACKAGE')),
  to_base_factor numeric(14,6),
  sort_order     integer not null default 0,
  active         boolean not null default true,
  updated_at timestamptz not null default now(),
  sync_seq   bigint not null default 0
);

create table device (
  id           uuid primary key,          -- gerado pelo app na instalação
  user_id      uuid not null references app_user(id),
  name         text,
  platform     text,
  app_version  text,
  push_token   text,
  created_at   timestamptz not null default now(),
  last_seen_at timestamptz,
  revoked_at   timestamptz,
  revoked_by   uuid references app_user(id)
);

create table management_record (
  id                   uuid primary key,
  record_type          text not null check (record_type in
                        ('PLANTING','INPUT_APPLICATION','HARVEST','INPUT_PURCHASE','OUTFLOW')),
  record_number        bigint unique default nextval('record_number_seq'),
  association_id       uuid not null references association(id),
  farmer_id            uuid not null references app_user(id),
  property_id          uuid not null references property(id),
  plot_id              uuid references plot(id),
  performed_on         date not null,
  notes                text,
  latitude             numeric(9,6),
  longitude            numeric(9,6),
  location_accuracy_m  numeric(8,1),
  location_captured_at timestamptz,
  device_id            uuid references device(id),
  source               text not null check (source in ('MOBILE','WEB')),
  created_by           uuid not null references app_user(id),
  updated_by           uuid not null references app_user(id),
  client_created_at    timestamptz,
  completeness         text not null check (completeness in ('COMPLETE','INCOMPLETE')),
  missing_fields       text[] not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);
create index ix_record_farmer_date on management_record (farmer_id, performed_on desc) where deleted_at is null;
create index ix_record_property    on management_record (property_id, record_type, performed_on);
create index ix_record_assoc_compl on management_record (association_id, completeness) where deleted_at is null;

create table planting (
  record_id            uuid primary key references management_record(id),
  crop_id              uuid references crop(id),
  crop_other_name      text,
  variety              text,
  planted_area         numeric(12,3),
  planted_area_unit    text references measurement_unit(code),
  planted_area_m2      numeric(14,2),
  propagation_type     text check (propagation_type in ('SEED','SEEDLING','CUTTING','OTHER')),
  propagation_quantity numeric(12,3),
  propagation_unit     text references measurement_unit(code),
  seed_origin          text check (seed_origin in ('OWN','ORGANIC_PURCHASED','CONVENTIONAL_PURCHASED','SEED_BANK')),
  seed_supplier        text,
  expected_harvest_on  date,
  cycle_status         text not null default 'ACTIVE' check (cycle_status in ('ACTIVE','FINISHED')),
  finished_on          date,
  check (crop_id is not null or crop_other_name is not null)
);

create table input_application (
  record_id         uuid primary key references management_record(id),
  planting_id       uuid references planting(record_id),
  input_id          uuid references farm_input(id),
  input_other_name  text,
  purpose           text check (purpose in ('FERTILIZATION','PEST_DISEASE_CONTROL','SOIL_CORRECTION','OTHER')),
  quantity          numeric(12,3),
  quantity_unit     text references measurement_unit(code),
  applied_area      numeric(12,3),
  applied_area_unit text references measurement_unit(code),
  applied_area_m2   numeric(14,2),
  check (input_id is not null or input_other_name is not null)
);

create table harvest (
  record_id           uuid primary key references management_record(id),
  planting_id         uuid not null references planting(record_id),
  quantity            numeric(12,3),
  quantity_unit       text references measurement_unit(code),
  harvested_area      numeric(12,3),
  harvested_area_unit text references measurement_unit(code),
  harvested_area_m2   numeric(14,2),
  is_final            boolean not null default false
);

create table input_purchase (
  record_id        uuid primary key references management_record(id),
  input_id         uuid references farm_input(id),
  input_other_name text,
  quantity         numeric(12,3),
  quantity_unit    text references measurement_unit(code),
  supplier         text,
  check (input_id is not null or input_other_name is not null)
);

create table outflow (
  record_id          uuid primary key references management_record(id),
  crop_id            uuid references crop(id),
  product_other_name text,
  planting_id        uuid references planting(record_id),
  quantity           numeric(12,3),
  quantity_unit      text references measurement_unit(code),
  destination        text check (destination in
                      ('FAIR','COOPERATIVE','PNAE','PAA','MIDDLEMAN','DIRECT_SALE','OWN_CONSUMPTION','DONATION','OTHER')),
  buyer_name         text,
  check (crop_id is not null or product_other_name is not null)
);

create table attachment (
  id               uuid primary key,
  association_id   uuid not null references association(id),
  owner_type       text not null check (owner_type in ('RECORD','PROPERTY','CERTIFICATION','ANNOUNCEMENT','VISIT','DOCUMENT')),
  owner_id         uuid not null,
  kind             text not null check (kind in ('PHOTO','AUDIO','DOCUMENT')),
  content_type     text not null,
  size_bytes       bigint,
  sha256           char(64),
  storage_key      text,
  duration_seconds integer,
  uploaded_at      timestamptz,
  created_by       uuid not null references app_user(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);
create index ix_attachment_owner on attachment (owner_type, owner_id);

create table correction_request (
  id          uuid primary key,
  record_id   uuid not null references management_record(id),
  message     text not null,
  status      text not null default 'OPEN' check (status in ('OPEN','RESOLVED')),
  created_by  uuid not null references app_user(id),
  resolved_by uuid references app_user(id),
  resolved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table announcement (
  id             uuid primary key,
  association_id uuid not null references association(id),
  type           text not null check (type in ('NOTICE','TIP')),
  title          text not null,
  body           text not null,
  group_id       uuid references spg_group(id),
  published_at   timestamptz not null default now(),
  created_by     uuid not null references app_user(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);

create table notification (
  id         uuid primary key,
  user_id    uuid not null references app_user(id),
  type       text not null,
  title      text not null,
  body       text not null,
  data       jsonb not null default '{}',
  read_at    timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0,
  sync_seq   bigint not null default 0
);
create index ix_notification_user on notification (user_id, created_at desc);

create table refresh_token (
  id         uuid primary key,
  user_id    uuid not null references app_user(id),
  device_id  uuid references device(id),
  family_id  uuid not null,
  token_hash char(64) not null unique,     -- SHA-256 do token (o token tem 256 bits aleatórios)
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  used_at    timestamptz,
  revoked_at timestamptz
);

create table access_code (
  id              uuid primary key,
  farmer_id       uuid not null references app_user(id),
  code_hash       text not null,          -- Argon2id
  expires_at      timestamptz not null,
  failed_attempts integer not null default 0,
  used_at         timestamptz,
  revoked_at      timestamptz,
  created_by      uuid not null references app_user(id),
  created_at      timestamptz not null default now()
);

create table login_challenge (
  id              uuid primary key,
  user_id         uuid not null references app_user(id),
  code_hash       text not null,
  expires_at      timestamptz not null,
  failed_attempts integer not null default 0,
  used_at         timestamptz,
  created_at      timestamptz not null default now()
);

create table user_token (
  id         uuid primary key,
  user_id    uuid not null references app_user(id),
  purpose    text not null check (purpose in ('PASSWORD_RESET','INVITE')),
  token_hash char(64) not null unique,
  expires_at timestamptz not null,
  used_at    timestamptz,
  created_at timestamptz not null default now()
);

create table sync_mutation (
  id           uuid primary key,          -- gerado pelo app para cada mutação
  user_id      uuid not null references app_user(id),
  device_id    uuid references device(id),
  entity       text not null,
  entity_id    uuid not null,
  status       text not null,
  response     jsonb not null,
  processed_at timestamptz not null default now()
);

-- Avisos já enviados pelas rotinas do SPG (RN30).
create table alert_log (
  subject_id uuid not null,
  alert_key  text not null,
  sent_at    timestamptz not null default now(),
  primary key (subject_id, alert_key)
);

-- Certificação participativa (doc 1, Parte 6).
create table collaborator (
  id             uuid primary key,
  association_id uuid not null references association(id),
  group_id       uuid references spg_group(id),
  name           text not null,
  kind           text not null check (kind in ('TECHNICIAN','CONSUMER','ORGANIZATION','OTHER')),
  organization   text,
  phone          text,
  notes          text,
  active         boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0
);

create table committee_member (
  id                   uuid primary key,
  association_id       uuid not null references association(id),
  farmer_id            uuid references app_user(id),
  collaborator_id      uuid references collaborator(id),
  represented_group_id uuid not null references spg_group(id),
  role                 text not null check (role in ('HOLDER','ALTERNATE')),
  started_on           date not null,
  ended_on             date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0,
  check ((farmer_id is null) <> (collaborator_id is null))
);

create table member_conflict (
  id             uuid primary key,
  association_id uuid not null references association(id),
  farmer_a_id    uuid not null references app_user(id),
  farmer_b_id    uuid not null references app_user(id),
  reason         text not null check (reason in ('FAMILY','SAME_HOUSEHOLD','BUSINESS','OTHER')),
  notes          text,
  created_at     timestamptz not null default now(),
  check (farmer_a_id < farmer_b_id),
  unique (farmer_a_id, farmer_b_id)
);

create table visit (
  id                      uuid primary key,
  association_id          uuid not null references association(id),
  property_id             uuid not null references property(id),
  group_id                uuid references spg_group(id),
  certification_id        uuid references certification(id),
  type                    text not null check (type in ('PEER','VERIFICATION')),
  unannounced             boolean not null default false,
  status                  text not null default 'PLANNED' check (status in ('PLANNED','DONE','CANCELLED')),
  planned_on              date not null,
  cancel_reason           text,
  recorder_participant_id uuid,                 -- referência adicionada depois de visit_participant
  done_on                 date,
  answers                 jsonb not null default '[]',
  issues_found            boolean,
  improvement_points      text,
  recommendations         text,
  notes                   text,
  latitude                numeric(9,6),
  longitude               numeric(9,6),
  location_accuracy_m     numeric(8,1),
  result_source           text check (result_source in ('MOBILE','WEB')),
  result_recorded_by      uuid references app_user(id),
  result_recorded_at      timestamptz,
  brief                   jsonb,
  brief_updated_at        timestamptz,
  created_by              uuid not null references app_user(id),
  updated_by              uuid not null references app_user(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  sync_seq   bigint not null default 0,
  check (type = 'VERIFICATION' or not unannounced),
  check (status <> 'DONE' or done_on is not null)
);
create index ix_visit_property on visit (property_id, type, done_on) where deleted_at is null;
create index ix_visit_planning on visit (association_id, status, planned_on) where deleted_at is null;

create table visit_participant (
  id                 uuid primary key,
  visit_id           uuid not null references visit(id),
  farmer_id          uuid references app_user(id),
  collaborator_id    uuid references collaborator(id),
  role               text not null check (role in ('VISITOR','OBSERVER')),
  suggested          boolean not null default false,
  suggestion_reasons text[] not null default '{}',
  attended           boolean,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  version    bigint not null default 0,
  check ((farmer_id is null) <> (collaborator_id is null))
);
create index ix_visit_participant_farmer on visit_participant (farmer_id);
alter table visit add constraint fk_visit_recorder
  foreign key (recorder_participant_id) references visit_participant(id);

create table visit_checklist_item (
  id               uuid primary key,
  code             text not null,
  association_id   uuid references association(id),   -- nulo = modelo padrão IN19-v1
  applies_to       text not null check (applies_to in ('PEER','VERIFICATION','BOTH')),
  question         text not null,
  formal_text      text not null,
  help             text,
  legal_reference  text,
  sort_order       integer not null default 0,
  template_version text not null,
  active           boolean not null default true,
  updated_at timestamptz not null default now(),
  sync_seq   bigint not null default 0
);
-- Um código por associação (o modelo padrão tem association_id nulo).
create unique index ux_visit_checklist_item on visit_checklist_item
  (coalesce(association_id, '00000000-0000-0000-0000-000000000000'::uuid), code);

create table generated_document (
  id                   uuid primary key,
  association_id       uuid not null references association(id),
  kind                 text not null check (kind in ('MEMBERSHIP_REQUEST','EVALUATION_REQUEST','VISIT_PLAN','VISIT_FORM',
                        'PEER_VISIT_RECORD','VERIFICATION_REPORT','CONFORMITY_DECISION','CERTIFICATE')),
  template_version     text not null,
  title                text not null,
  farmer_id            uuid references app_user(id),
  property_id          uuid references property(id),
  group_id             uuid references spg_group(id),
  visit_id             uuid references visit(id),
  certification_id     uuid references certification(id),
  period_from          date,
  period_to            date,
  verification_code    char(8) not null unique,
  sha256               char(64) not null,
  storage_key          text not null,
  size_bytes           bigint not null,
  data_snapshot        jsonb not null,
  warnings             text[] not null default '{}',
  status               text not null default 'GENERATED'
                       check (status in ('GENERATED','SIGNED','SUPERSEDED','CANCELLED')),
  signed_attachment_id uuid references attachment(id),
  superseded_by_id     uuid references generated_document(id),
  cancel_reason        text,
  generated_by         uuid not null references app_user(id),
  generated_at         timestamptz not null default now()
);
create index ix_generated_document_subject on generated_document (kind, property_id);

-- Trigger de sync_seq em todas as tabelas sincronizáveis.
do $$
declare t text;
begin
  foreach t in array array['association','spg_group','app_user','property','plot','worker','certification',
                           'crop','farm_input','measurement_unit','management_record','attachment',
                           'correction_request','announcement','notification','visit','visit_checklist_item']
  loop
    execute format('create trigger trg_%1$s_sync_seq before insert or update on %1$s
                    for each row execute function set_sync_seq()', t);
    execute format('create index ix_%1$s_sync_seq on %1$s (sync_seq)', t);
  end loop;
end $$;
