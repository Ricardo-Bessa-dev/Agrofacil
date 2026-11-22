// Unidades de quantidade dos registros de manejo: códigos aceitos pela API, nomes e opções do formulário.

/** Campos da unidade que chegam do catálogo (UnitView da API). */
export interface UnitOption {
  code?: string;
  namePlural?: string;
  kind?: string;
  sortOrder?: number;
  active?: boolean;
}

export interface QuantityUnitOption {
  code: string;
  label: string;
}

/**
 * Cópia das unidades que não são de área, na ordem da migração V3__seed_reference_data.sql.
 * Serve para exibir nomes e para preencher o formulário enquanto o catálogo não chegou.
 * Atenção: os códigos precisam ser iguais aos da tabela measurement_unit; outro código é recusado.
 */
export const QUANTITY_UNIT_LABELS: Record<string, { singular: string; plural: string }> = {
  KILOGRAM: { singular: "quilo", plural: "quilos" },
  GRAM: { singular: "grama", plural: "gramas" },
  LITER: { singular: "litro", plural: "litros" },
  MILLILITER: { singular: "mililitro", plural: "mililitros" },
  UNIT: { singular: "unidade", plural: "unidades" },
  DOZEN: { singular: "dúzia", plural: "dúzias" },
  SEEDLING: { singular: "muda", plural: "mudas" },
  BOX: { singular: "caixa", plural: "caixas" },
  BAG: { singular: "saco", plural: "sacos" },
  BUNCH: { singular: "molho", plural: "molhos" },
  CAN: { singular: "lata", plural: "latas" },
  CART: { singular: "carroça", plural: "carroças" },
};

/** Unidade que já vem marcada em um registro novo. */
export const DEFAULT_QUANTITY_UNIT = "KILOGRAM";
/** Na aplicação de insumo o mais comum é calda ou biofertilizante, medidos em litros. */
export const DEFAULT_APPLICATION_UNIT = "LITER";

/**
 * Opções de unidade para um campo de quantidade: só unidades ativas e que não são de área (RN28).
 * Sem catálogo, usa a lista padrão. A unidade já escolhida continua na lista mesmo que a associação
 * a tenha desativado, para a edição de um registro antigo não trocar a unidade sem aviso.
 */
export function quantityUnitOptions(
  catalog: readonly UnitOption[] | null | undefined,
  selectedCode?: string,
): QuantityUnitOption[] {
  const options = (catalog ?? [])
    .filter((unit) => unit.kind !== "AREA" && unit.active !== false)
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    .flatMap((unit) =>
      unit.code ? [{ code: unit.code, label: unit.namePlural ?? pluralLabel(unit.code) }] : [],
    );
  const available = options.length > 0 ? options : defaultOptions();

  if (selectedCode && !available.some((unit) => unit.code === selectedCode)) {
    return [...available, { code: selectedCode, label: pluralLabel(selectedCode) }];
  }
  return available;
}

function defaultOptions(): QuantityUnitOption[] {
  return Object.entries(QUANTITY_UNIT_LABELS).map(([code, { plural }]) => ({
    code,
    label: plural,
  }));
}

function pluralLabel(code: string): string {
  return QUANTITY_UNIT_LABELS[code]?.plural ?? code.toLowerCase();
}
