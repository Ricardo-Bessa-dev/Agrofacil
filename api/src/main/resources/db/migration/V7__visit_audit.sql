-- Histórico (Envers) das visitas do SPG: a correção do resultado fica registrada (RN35).
-- O resumo (brief) não é auditado: é recalculado todo dia a partir dos registros (RN36).

create table visit_aud (
  id                      uuid not null,
  rev                     bigint not null references audit_revision(id),
  revtype                 smallint,
  association_id          uuid,
  property_id             uuid,
  group_id                uuid,
  certification_id        uuid,
  type                    text,
  unannounced             boolean,
  status                  text,
  planned_on              date,
  cancel_reason           text,
  recorder_participant_id uuid,
  done_on                 date,
  answers                 jsonb,
  issues_found            boolean,
  improvement_points      text,
  recommendations         text,
  notes                   text,
  latitude                numeric(9,6),
  longitude               numeric(9,6),
  location_accuracy_m     numeric(8,1),
  result_source           text,
  result_recorded_by      uuid,
  result_recorded_at      timestamptz,
  created_by              uuid,
  updated_by              uuid,
  created_at              timestamptz,
  updated_at              timestamptz,
  deleted_at              timestamptz,
  primary key (rev, id)
);

create index ix_visit_participant_visit on visit_participant (visit_id) where deleted_at is null;
