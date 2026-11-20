// Conversão de áreas (RN14, ADR-12) e formatação numérica no padrão brasileiro.
import { AREA_UNIT_LABELS, AREA_UNIT_PLURAL_LABELS } from "./labels";

export type AreaUnitCode = "HECTARE" | "SQUARE_METER" | "TAREFA";

export const DEFAULT_TAREFA_M2 = 4356;
export const HECTARE_M2 = 10000;

/** Converte um valor de área para metros quadrados (m²). */
export function toSquareMeters(
  value: number,
  unit: AreaUnitCode,
  tarefaM2: number = DEFAULT_TAREFA_M2,
): number {
  if (value <= 0 || Number.isNaN(value)) {
    throw new Error("O valor da área deve ser maior que zero.");
  }
  switch (unit) {
    case "SQUARE_METER":
      return value;
    case "HECTARE":
      return value * HECTARE_M2;
    case "TAREFA":
      return value * tarefaM2;
    default:
      throw new Error(`Unidade de área desconhecida: ${String(unit)}`);
  }
}

/** Formata um número usando vírgula decimal: 12.5 -> "12,5". */
export function formatNumberPtBr(value: number, maxDecimals = 2): string {
  return new Intl.NumberFormat("pt-BR", {
    minimumFractionDigits: 0,
    maximumFractionDigits: maxDecimals,
  }).format(value);
}

/** Formata um objeto de área para exibição: { value: 12.5, unit: "HECTARE" } -> "12,5 hectares". */
export function formatArea(area?: { value?: number; unit?: string } | null): string {
  if (!area || area.value === undefined || !area.unit) return "—";
  const formatted = formatNumberPtBr(area.value);
  const label =
    area.value === 1
      ? (AREA_UNIT_LABELS[area.unit] ?? area.unit)
      : (AREA_UNIT_PLURAL_LABELS[area.unit] ?? area.unit);
  return `${formatted} ${label}`;
}

/** Formata uma quantidade com sua unidade: { value: 30, unit: "MOLHO" } -> "30 molhos". */
export function formatQuantity(
  quantity?: { value?: number; unit?: string } | null,
  unitLabels?: Record<string, { singular: string; plural: string }>,
): string {
  if (!quantity || quantity.value === undefined || !quantity.unit) return "—";
  const formatted = formatNumberPtBr(quantity.value);
  const unitInfo = unitLabels?.[quantity.unit];
  if (unitInfo) {
    return `${formatted} ${quantity.value === 1 ? unitInfo.singular : unitInfo.plural}`;
  }
  return `${formatted} ${quantity.unit.toLowerCase()}`;
}
