// Quanto da propriedade ainda cabe em áreas (talhões, canteiros), para avisar antes de salvar (RN41).
import { HECTARE_M2, toSquareMeters, type AreaUnitCode } from "./units";

/** Mesma folga da API: cada área é arredondada à parte, então a soma pode passar alguns centavos de m². */
export const AREA_ROUNDING_TOLERANCE_M2 = 1;

type AreaWithM2 = { value?: number; unit?: string; m2?: number } | null | undefined;
type PlotLike = { id?: string; name?: string; area?: AreaWithM2 };

/** Nome da área criada junto com a propriedade (RN06), igual ao da API. */
export const MAIN_PLOT_NAME = "Área principal";

/**
 * Área livre da propriedade, em m²: o tamanho dela menos a soma das áreas (sem a que está sendo editada).
 * Áreas sem tamanho não contam.
 * @returns null se a propriedade não tem tamanho (então não há limite)
 */
export function freePropertyAreaM2(
  propertyTotal: AreaWithM2,
  plots: PlotLike[],
  exceptPlotId?: string,
): number | null {
  if (propertyTotal?.m2 === undefined) return null;
  const usedM2 = plots
    .filter((p) => exceptPlotId === undefined || p.id !== exceptPlotId)
    .reduce((sum, p) => sum + (p.area?.m2 ?? 0), 0);
  return Math.max(0, propertyTotal.m2 - usedM2);
}

/** Converte m² para a unidade escolhida (inverso de toSquareMeters). */
export function fromSquareMeters(m2: number, unit: AreaUnitCode, tarefaM2: number): number {
  switch (unit) {
    case "SQUARE_METER":
      return m2;
    case "HECTARE":
      return m2 / HECTARE_M2;
    case "TAREFA":
      return m2 / tarefaM2;
  }
}

/** true se a área digitada cabe no espaço livre (com a folga de arredondamento). */
export function plotFitsInProperty(
  freeM2: number,
  value: number,
  unit: AreaUnitCode,
  tarefaM2: number,
): boolean {
  return toSquareMeters(value, unit, tarefaM2) <= freeM2 + AREA_ROUNDING_TOLERANCE_M2;
}

/**
 * Quanto as áreas ocupam, em m², para conferir um novo tamanho da propriedade (RN41).
 * Igual à API: a "Área principal" sozinha e do tamanho atual acompanha a mudança, então não limita.
 * @returns 0 quando não há limite
 */
export function plotsOccupiedM2(currentTotal: AreaWithM2, plots: PlotLike[]): number {
  const only = plots.length === 1 ? plots[0] : undefined;
  if (only?.name === MAIN_PLOT_NAME && only.area?.m2 === currentTotal?.m2) return 0;
  return plots.reduce((sum, p) => sum + (p.area?.m2 ?? 0), 0);
}
