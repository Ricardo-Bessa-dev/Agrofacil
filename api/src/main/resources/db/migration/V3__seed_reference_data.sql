-- V3__seed_reference_data.sql: unidades, categorias, culturas, insumos e o roteiro de visita IN19-v1.
-- Listas iniciais [confirmar com a associação] (doc 1 §1.8); a associação completa pelo painel.

-- to_base_factor: para m² (área), kg (massa), L (volume) ou unidade; nulo na tarefa (usa association.tarefa_m2) e nas embalagens.
insert into measurement_unit (code, name_singular, name_plural, kind, to_base_factor, sort_order) values
  ('HECTARE', 'hectare', 'hectares', 'AREA', 10000, 1),
  ('SQUARE_METER', 'metro quadrado', 'metros quadrados', 'AREA', 1, 2),
  ('TAREFA', 'tarefa', 'tarefas', 'AREA', null, 3),
  ('KILOGRAM', 'quilo', 'quilos', 'MASS', 1, 10),
  ('GRAM', 'grama', 'gramas', 'MASS', 0.001, 11),
  ('LITER', 'litro', 'litros', 'VOLUME', 1, 20),
  ('MILLILITER', 'mililitro', 'mililitros', 'VOLUME', 0.001, 21),
  ('UNIT', 'unidade', 'unidades', 'COUNT', 1, 30),
  ('DOZEN', 'dúzia', 'dúzias', 'COUNT', 12, 31),
  ('SEEDLING', 'muda', 'mudas', 'COUNT', 1, 32),
  ('BOX', 'caixa', 'caixas', 'PACKAGE', null, 40),
  ('BAG', 'saco', 'sacos', 'PACKAGE', null, 41),
  ('BUNCH', 'molho', 'molhos', 'PACKAGE', null, 42),
  ('CAN', 'lata', 'latas', 'PACKAGE', null, 43),
  ('CART', 'carroça', 'carroças', 'PACKAGE', null, 44);

insert into crop_category (id, name, icon) values
  ('524541fd-244b-50d5-ab39-38611350964f', 'Hortaliças', 'leaf'),
  ('2728b9b6-8484-5731-ad44-da5cd5994465', 'Frutas', 'fruit'),
  ('4bc22910-5bbf-5441-bd39-8188ba6a6809', 'Grãos', 'grain'),
  ('5e7a764e-d01b-54b4-b538-2bd379fbde12', 'Raízes e tubérculos', 'root'),
  ('d14df768-ce4c-5c15-8d93-5282743e9c64', 'Forrageiras', 'forage'),
  ('f8003067-620b-5702-b594-0b6179cb11da', 'Ervas e temperos', 'herb');

-- Culturas globais (association_id nulo).
insert into crop (id, category_id, name, icon) values
  ('3f38842d-84ad-59c8-9c28-7d7ed230145c', '524541fd-244b-50d5-ab39-38611350964f', 'Alface', 'lettuce'),
  ('5647ca08-024a-567d-8948-45620f04c418', '524541fd-244b-50d5-ab39-38611350964f', 'Couve', 'kale'),
  ('44c06a94-baae-5b95-b65b-5f014ca9db92', '524541fd-244b-50d5-ab39-38611350964f', 'Tomate', 'tomato'),
  ('08a2d708-356e-50ef-a64e-b12ad3238f94', '524541fd-244b-50d5-ab39-38611350964f', 'Pimentão', 'pepper'),
  ('412b00c1-3355-5374-9f06-1452f19fda10', '524541fd-244b-50d5-ab39-38611350964f', 'Abóbora', 'pumpkin'),
  ('a5b768b5-a9e6-58a4-a87e-fe57945de91c', '524541fd-244b-50d5-ab39-38611350964f', 'Quiabo', 'okra'),
  ('31f8a327-b259-5670-994e-1e62322a35f9', '524541fd-244b-50d5-ab39-38611350964f', 'Maxixe', 'gherkin'),
  ('2e79bfae-151b-5543-91a9-b1e39236a163', '524541fd-244b-50d5-ab39-38611350964f', 'Beterraba', 'beet'),
  ('dc159484-49c8-5ecc-9acc-4c76b108e072', '524541fd-244b-50d5-ab39-38611350964f', 'Cenoura', 'carrot'),
  ('1b43d81f-2886-5dc7-9f5d-26d2220a2c6d', 'f8003067-620b-5702-b594-0b6179cb11da', 'Coentro', 'coriander'),
  ('d40b84fd-2151-56d7-847a-3e29060acb70', 'f8003067-620b-5702-b594-0b6179cb11da', 'Cebolinha', 'chives'),
  ('0efdda63-5335-50ed-87bf-e09868cacc6f', 'f8003067-620b-5702-b594-0b6179cb11da', 'Salsa', 'parsley'),
  ('4d491300-6aff-55c9-a23d-1f7fb2c9a6fe', '2728b9b6-8484-5731-ad44-da5cd5994465', 'Umbu', 'umbu'),
  ('6a284853-6d17-57f9-9cbc-95631c1418ac', '2728b9b6-8484-5731-ad44-da5cd5994465', 'Manga', 'mango'),
  ('bc4cc3c3-67be-5673-99cc-8f1c3f645fbc', '2728b9b6-8484-5731-ad44-da5cd5994465', 'Acerola', 'acerola'),
  ('7fc48877-cf1d-5232-a146-c3669b10ae3f', '2728b9b6-8484-5731-ad44-da5cd5994465', 'Banana', 'banana'),
  ('baddd30d-6ffd-54c0-9409-8519cb975c3f', '2728b9b6-8484-5731-ad44-da5cd5994465', 'Maracujá', 'passion-fruit'),
  ('8320c3b5-4d10-5b16-8e45-b2ae7587141d', '2728b9b6-8484-5731-ad44-da5cd5994465', 'Mamão', 'papaya'),
  ('b858a963-2a98-5cea-a409-33a2cce6cb68', '2728b9b6-8484-5731-ad44-da5cd5994465', 'Melancia', 'watermelon'),
  ('0ef9cebc-6ae9-5941-a282-5c21ff015c5f', '2728b9b6-8484-5731-ad44-da5cd5994465', 'Goiaba', 'guava'),
  ('2ce8b7f7-2cbe-55e8-a516-73461529eb12', '4bc22910-5bbf-5441-bd39-8188ba6a6809', 'Feijão', 'bean'),
  ('589bde7a-1384-5380-bad7-314d5c3cae0e', '4bc22910-5bbf-5441-bd39-8188ba6a6809', 'Milho', 'corn'),
  ('687ae4ad-803f-5057-93cf-7d6acce4b96a', '4bc22910-5bbf-5441-bd39-8188ba6a6809', 'Mamona', 'castor-bean'),
  ('95a58c47-f766-57e4-bbd5-3684834c5771', '4bc22910-5bbf-5441-bd39-8188ba6a6809', 'Andu', 'pigeon-pea'),
  ('5819414c-ba7e-5f31-8948-8cc152b7ec3d', '5e7a764e-d01b-54b4-b538-2bd379fbde12', 'Mandioca', 'cassava'),
  ('6a5f5d1d-f5c8-5911-8683-7e9fd4cdc7cf', '5e7a764e-d01b-54b4-b538-2bd379fbde12', 'Batata-doce', 'sweet-potato'),
  ('369c84ce-525c-500e-964b-30d2cc147cff', '5e7a764e-d01b-54b4-b538-2bd379fbde12', 'Inhame', 'yam'),
  ('1d56b5bb-1cb1-508b-a202-c3b527a733a2', 'd14df768-ce4c-5c15-8d93-5282743e9c64', 'Palma forrageira', 'cactus'),
  ('e8d8e808-1ea2-57da-8e70-4ae8ac9928bf', 'd14df768-ce4c-5c15-8d93-5282743e9c64', 'Sorgo', 'sorghum');

-- Insumos globais semeados dos Anexos V e VII da Portaria 52/2021 (ADR-07).
insert into farm_input (id, name, input_type, regulatory_reference, needs_authorization, home_made) values
  ('aacabd88-c335-5c88-8621-8a01d2940539', 'Esterco bovino curtido', 'FERTILIZER', 'Portaria MAPA 52/2021, Anexo V', false, true),
  ('28a314d6-614c-5a98-9390-37cfe7e92e55', 'Esterco de caprinos e ovinos', 'FERTILIZER', 'Portaria MAPA 52/2021, Anexo V', false, true),
  ('25f967e5-272f-501d-8dcc-ee040a1e1a6e', 'Composto orgânico', 'FERTILIZER', 'Portaria MAPA 52/2021, Anexo V', false, true),
  ('a4b65b73-de74-5e43-a119-83affdd49aa5', 'Húmus de minhoca', 'FERTILIZER', 'Portaria MAPA 52/2021, Anexo V', false, true),
  ('9460691a-a310-52e4-a6b8-5cc6ed6cd9a1', 'Torta de mamona', 'FERTILIZER', 'Portaria MAPA 52/2021, Anexo V', false, false),
  ('8c1d4308-29e7-59a2-ace7-d6bb15a4a8ac', 'Biofertilizante', 'BIOFERTILIZER', 'Portaria MAPA 52/2021, Anexo V', false, true),
  ('78357f22-5af2-5633-9212-4ed44266951a', 'Calcário', 'SOIL_CORRECTIVE', 'Portaria MAPA 52/2021, Anexo V', false, false),
  ('63edd25e-8705-5806-8848-629baefa089b', 'Cinza vegetal', 'SOIL_CORRECTIVE', 'Portaria MAPA 52/2021, Anexo V', false, true),
  ('00551f0e-4fad-5301-ad8e-f88963ef2e56', 'Calda bordalesa', 'PEST_DISEASE_CONTROL', 'Portaria MAPA 52/2021, Anexo VII', false, true),
  ('03690086-cbc8-5842-af0b-10d23a58101e', 'Calda sulfocálcica', 'PEST_DISEASE_CONTROL', 'Portaria MAPA 52/2021, Anexo VII', false, true),
  ('89e148b6-16cb-5520-88b2-407005a76fc6', 'Extrato de nim', 'PEST_DISEASE_CONTROL', 'Portaria MAPA 52/2021, Anexo VII', false, true),
  ('33dcea17-5367-5028-8dab-60220626831a', 'Bacillus thuringiensis', 'PEST_DISEASE_CONTROL', 'Portaria MAPA 52/2021, Anexo VII', false, false),
  ('8d11d961-5845-5862-bdfa-97ea78af5271', 'Trichoderma', 'INOCULANT', 'Portaria MAPA 52/2021, Anexo VII', false, false);

-- Roteiro da visita, modelo IN19-v1 (doc 1 §6.6). association_id nulo = modelo padrão.
insert into visit_checklist_item (id, code, applies_to, question, formal_text, legal_reference, sort_order, template_version) values
  ('d74d7384-4f86-5129-b616-8559764f6766', 'RECORDS_UP_TO_DATE', 'BOTH', 'Os registros da roça estão em dia (no app ou no caderno)?', 'Registros de plantio, colheita, uso e compra de insumos e saídas atualizados', 'Portaria MAPA 52/2021, arts. 11 e 119', 10, 'IN19-v1'),
  ('7def7528-91d9-573a-81d0-2bc8736520dc', 'INPUTS_ALLOWED', 'BOTH', 'Os adubos e remédios usados são permitidos?', 'Insumos utilizados constam dos Anexos da Portaria 52/2021 ou foram autorizados pelo OAC', 'Portaria MAPA 52/2021, arts. 117 e 120', 20, 'IN19-v1'),
  ('f70498a8-f36a-5b16-b356-602a82d0a9a1', 'NO_FORBIDDEN_STORED', 'BOTH', 'No lugar onde guarda os produtos, não tem nada proibido?', 'Não foram encontrados insumos proibidos armazenados na unidade', 'Portaria MAPA 52/2021, art. 120', 30, 'IN19-v1'),
  ('c574ce0d-0da5-5d17-b342-cff0a89dfc05', 'SEED_ORIGIN_KNOWN', 'BOTH', 'Sabe de onde vieram as sementes e mudas?', 'Origem das sementes e mudas conhecida; material não orgânico com autorização', 'Portaria MAPA 52/2021, art. 103', 40, 'IN19-v1'),
  ('2f043d65-9c38-5f56-a817-e79ad9ccce70', 'AREAS_SEPARATED', 'BOTH', 'As áreas orgânicas estão separadas das outras?', 'Áreas em conversão ou não orgânicas separadas e demarcadas', 'Portaria MAPA 52/2021, arts. 21 e 22', 50, 'IN19-v1'),
  ('56a78ee1-5c42-5ddd-b1be-6e35de4bbf13', 'CONTAMINATION_PROTECTED', 'BOTH', 'A roça está protegida de veneno vindo do vizinho ou da água?', 'Medidas de prevenção de contaminação (barreiras, distâncias, qualidade da água)', 'Portaria MAPA 52/2021, art. 12', 60, 'IN19-v1'),
  ('05f1a212-c181-5d32-b760-d2d08db36253', 'SOIL_WATER_CARE', 'BOTH', 'Cuidam do solo e da água (cobertura, sem queimada, sem desperdício)?', 'Práticas de conservação do solo e da água', 'Portaria MAPA 52/2021, art. 12', 70, 'IN19-v1'),
  ('f44fd737-0866-542a-abd8-22044f8627b7', 'WASTE_HANDLED', 'BOTH', 'O lixo e as embalagens têm destino certo?', 'Manejo adequado de resíduos e embalagens', 'Portaria MAPA 52/2021, art. 12', 80, 'IN19-v1'),
  ('6ccb577f-a827-5d83-ae8a-99bdfb613b5a', 'BIODIVERSITY', 'BOTH', 'Tem variedade de plantas, mata ou cerca viva?', 'Manutenção e promoção da biodiversidade', 'Portaria MAPA 52/2021, art. 12', 90, 'IN19-v1'),
  ('e8cef7b2-0787-5228-aa80-fd9a663d72ce', 'POST_HARVEST_OK', 'BOTH', 'Colheita, lavagem e transporte são feitos sem misturar com produto de fora?', 'Pós-colheita, armazenamento e transporte sem risco de mistura', 'Portaria MAPA 52/2021, art. 12', 100, 'IN19-v1'),
  ('cce4646a-9459-5452-8e2d-83de5bc42209', 'MANAGEMENT_PLAN_MATCHES', 'VERIFICATION', 'O plano de manejo está na propriedade e bate com o que vimos?', 'Plano de Manejo Orgânico disponível e coerente com a prática observada', 'Portaria MAPA 52/2021, art. 12; IN MAPA 19/2009, art. 79', 110, 'IN19-v1'),
  ('77ce1a67-43a3-5aac-adb9-dd4bcb11726d', 'FAIR_WORK', 'BOTH', 'As pessoas que trabalham aqui são bem tratadas, sem trabalho de criança?', 'Relações de trabalho justas, sem trabalho infantil', 'Lei 10.831/2003, art. 1º', 120, 'IN19-v1'),
  ('51722895-c766-50f8-954f-1e0cf7918cb5', 'TAKES_PART', 'BOTH', 'A família participa das reuniões e visitas do grupo?', 'Participação do fornecedor nas atividades do SPG', 'IN MAPA 19/2009, art. 77, parágrafo único', 130, 'IN19-v1'),
  ('9c98de80-9137-5002-b53d-89a6e3eb1aec', 'PREVIOUS_ADVICE_DONE', 'BOTH', 'O que foi combinado na última visita foi feito?', 'Recomendações da visita anterior atendidas', 'IN MAPA 19/2009, art. 87', 140, 'IN19-v1');
