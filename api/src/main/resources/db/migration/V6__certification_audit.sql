-- Histórico (Envers) das certificações, que passam a ser editadas pelo painel no marco M7 (doc 1 §2.3.7 [A]).
-- Produtos e trabalhadores do certificado não são auditados: mudam junto com a linha principal.

create table certification_aud (
  id            uuid not null,
  rev           bigint not null references audit_revision(id),
  revtype       smallint,
  property_id   uuid,
  number        text,
  issued_on     date,
  expires_on    date,
  manual_status text,
  status_reason text,
  scope         text,
  previous_id   uuid,
  notes         text,
  created_by    uuid,
  created_at    timestamptz,
  updated_at    timestamptz,
  deleted_at    timestamptz,
  primary key (rev, id)
);

-- Lista de certificações e rotina de alertas buscam as certificações atuais pela propriedade.
create index ix_certification_product_cert on certification_product (certification_id);
