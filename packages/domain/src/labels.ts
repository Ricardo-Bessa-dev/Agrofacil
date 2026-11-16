// Rótulos em português para todos os enums do domínio (doc 3 §B.4 e §A.9).

export const RECORD_TYPE_LABELS: Record<string, string> = {
  PLANTING: "Plantio",
  INPUT_APPLICATION: "Aplicação de Insumo",
  HARVEST: "Colheita",
  INPUT_PURCHASE: "Compra de Insumo",
  OUTFLOW: "Saída / Venda",
};

export const RECORD_TYPE_FARMER_LABELS: Record<string, string> = {
  PLANTING: "Plantei",
  INPUT_APPLICATION: "Usei adubo ou remédio",
  HARVEST: "Colhi",
  INPUT_PURCHASE: "Comprei produto para a roça",
  OUTFLOW: "Vendi ou entreguei",
};

export const PLOT_STATUS_LABELS: Record<string, string> = {
  ORGANIC: "Orgânica",
  IN_CONVERSION: "Em conversão",
  NON_ORGANIC: "Não orgânica",
  NOT_DEFINED: "A definir pela associação",
};

export const WORKER_RELATIONSHIP_LABELS: Record<string, string> = {
  FAMILY: "Familiar",
  EMPLOYEE: "Contratado",
  PARTNER: "Meeiro / Parceiro",
  OTHER: "Outro",
};

export const PROPAGATION_TYPE_LABELS: Record<string, string> = {
  SEED: "Semente",
  SEEDLING: "Muda",
  CUTTING: "Rama / Maniva / Estaca",
  OTHER: "Outro",
};

export const SEED_ORIGIN_LABELS: Record<string, string> = {
  OWN: "Guardei da minha roça",
  SEED_BANK: "Banco ou troca de sementes",
  ORGANIC_PURCHASED: "Comprei ou ganhei orgânica",
  CONVENTIONAL_PURCHASED: "Comprei ou ganhei comum",
};

export const APPLICATION_PURPOSE_LABELS: Record<string, string> = {
  FERTILIZATION: "Adubar",
  PEST_DISEASE_CONTROL: "Combater praga ou doença",
  SOIL_CORRECTION: "Corrigir o solo",
  OTHER: "Outro",
};

export const OUTFLOW_DESTINATION_LABELS: Record<string, string> = {
  FAIR: "Feira livre",
  COOPERATIVE: "Cooperativa / Associação",
  PNAE: "Merenda escolar (PNAE)",
  PAA: "Compra do governo (PAA)",
  MIDDLEMAN: "Atravessador",
  DIRECT_SALE: "Venda direta",
  OWN_CONSUMPTION: "Consumo da família",
  DONATION: "Doação",
  OTHER: "Outro",
};

export const AREA_UNIT_LABELS: Record<string, string> = {
  TAREFA: "tarefa",
  HECTARE: "hectare",
  SQUARE_METER: "m²",
};

export const AREA_UNIT_PLURAL_LABELS: Record<string, string> = {
  TAREFA: "tarefas",
  HECTARE: "hectares",
  SQUARE_METER: "m²",
};

export const CERTIFICATION_STATUS_LABELS: Record<string, string> = {
  VALID: "Em dia",
  EXPIRING: "Vencendo em breve",
  EXPIRED: "Vencida",
  SUSPENDED: "Suspensa",
  CANCELLED: "Cancelada",
  NONE: "Sem certificação",
};

export const INPUT_TYPE_LABELS: Record<string, string> = {
  FERTILIZER: "Fertilizante / Adubo",
  SOIL_CORRECTIVE: "Corretivo do solo",
  BIOFERTILIZER: "Biofertilizante",
  PEST_DISEASE_CONTROL: "Calda / Controle de pragas",
  INOCULANT: "Inoculante",
  OTHER: "Outro",
};

export const VISIT_TYPE_LABELS: Record<string, string> = {
  PEER: "Visita do grupo",
  VERIFICATION: "Visita da comissão",
};

export const VISIT_STATUS_LABELS: Record<string, string> = {
  PLANNED: "Agendada",
  DONE: "Realizada",
  CANCELLED: "Cancelada",
};

export const VISIT_CYCLE_STATE_LABELS: Record<string, string> = {
  UP_TO_DATE: "Em dia",
  PENDING: "Pendente",
  URGENT: "Urgente",
};

export const DOCUMENT_KIND_LABELS: Record<string, string> = {
  MEMBERSHIP_REQUEST: "D1 · Requerimento de Adesão",
  EVALUATION_REQUEST: "D2 · Solicitação de Avaliação",
  VISIT_PLAN: "D3 · Plano de Visitas",
  VISIT_FORM: "D4 · Roteiro de Visita em Branco",
  PEER_VISIT_RECORD: "D5 · Registro de Visita do Grupo",
  VERIFICATION_REPORT: "D6 · Relatório de Visita da Comissão",
  CONFORMITY_DECISION: "D7 · Parecer de Conformidade",
  CERTIFICATE: "D8 · Certificado de Conformidade (Modelo)",
};

export const DOCUMENT_STATUS_LABELS: Record<string, string> = {
  GENERATED: "Gerado",
  SIGNED: "Assinado",
  SUPERSEDED: "Substituído",
  CANCELLED: "Cancelado",
};

export const MISSING_FIELD_LABELS: Record<string, string> = {
  plantedArea: "Tamanho da área plantada",
  seedOrigin: "Origem da semente ou muda",
  expectedHarvestOn: "Previsão de colheita",
  quantity: "Quantidade",
  purpose: "Finalidade da aplicação",
  supplierOrReceipt: "Fornecedor ou foto da nota",
  destination: "Destino da venda ou entrega",
  plantingId: "Plantio vinculado",
  appliedArea: "Área aplicada",
  harvestedArea: "Área colhida",
  supplier: "Fornecedor",
};

export const USER_STATUS_LABELS: Record<string, string> = {
  ACTIVE: "Ativo",
  INACTIVE: "Inativo",
};

export const COMPLETENESS_LABELS: Record<string, string> = {
  COMPLETE: "Completo",
  INCOMPLETE: "Incompleto",
};

export const RECORD_SOURCE_LABELS: Record<string, string> = {
  MOBILE: "Celular do agricultor",
  WEB: "Painel da coordenação",
};

export const ANNOUNCEMENT_TYPE_LABELS: Record<string, string> = {
  NOTICE: "Aviso da associação",
  TIP: "Dica técnica",
};

export const FARM_INPUT_TYPE_LABELS = INPUT_TYPE_LABELS;

export const COLLABORATOR_KIND_LABELS: Record<string, string> = {
  TECHNICIAN: "Técnico(a) / Extensão Rural",
  CONSUMER: "Consumidor(a)",
  ORGANIZATION: "Representante de Entidade",
  OTHER: "Outro",
};

export const COMMITTEE_ROLE_LABELS: Record<string, string> = {
  HOLDER: "Titular",
  ALTERNATE: "Suplente",
};

export const CONFLICT_REASON_LABELS: Record<string, string> = {
  FAMILY: "Parentesco familiar",
  SAME_HOUSEHOLD: "Mesma residência",
  BUSINESS: "Sociedade comercial",
  OTHER: "Outro impedimento",
};
