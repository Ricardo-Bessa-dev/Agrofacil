-- V5__seed_association.sql: a associação única, com id fixo e conhecido (ADR-14, doc 2 §1.5).
-- Nome e OPAC são provisórios. Eles e os demais campos (CNPJ, contatos, WhatsApp) são preenchidos pelo
-- coordenador no painel.
insert into association (id, name, opac_name)
values ('00000000-0000-0000-0000-000000000001', 'Associação (defina o nome no painel)', 'OPAC (defina o nome no painel)');
