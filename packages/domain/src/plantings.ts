// Escolha do plantio ao registrar aplicação, colheita ou saída: lista, rótulo e produto sugerido (RN11, RN42).
import { formatDatePtBr } from "./dates";

/** Campos da linha de plantio (RecordListItem da API) usados na escolha. */
export interface PlantingItem {
  id?: string;
  subject?: string;
  performedOn?: string;
  plot?: { name?: string } | null;
  planting?: {
    cropId?: string;
    cropOtherName?: string;
    cycleStatus?: string;
    finishedOn?: string;
  } | null;
}

export interface PlantingGroups<T> {
  active: T[];
  /** Só os encerrados que devem aparecer na lista. */
  finished: T[];
  /** Há encerrados fora da lista: mostrar "Ver plantações terminadas". */
  hasHiddenFinished: boolean;
}

export function isPlantingFinished(item: PlantingItem): boolean {
  return item.planting?.cycleStatus === "FINISHED";
}

/**
 * Separa os plantios em andamento dos encerrados.
 * O plantio já escolhido sempre aparece, para a edição não trocar o vínculo sem aviso.
 */
export function groupPlantings<T extends PlantingItem>(
  items: readonly T[],
  options: { includeFinished: boolean; selectedId?: string },
): PlantingGroups<T> {
  const allFinished = items.filter(isPlantingFinished);
  const finished = allFinished.filter(
    (item) => options.includeFinished || (item.id !== undefined && item.id === options.selectedId),
  );
  return {
    active: items.filter((item) => !isPlantingFinished(item)),
    finished,
    hasHiddenFinished: finished.length < allFinished.length,
  };
}

/** A venda vem depois da colheita, então os plantios encerrados já aparecem nela (RN42). */
export function showsFinishedPlantings(recordType: string): boolean {
  return recordType === "OUTFLOW";
}

/** Ex.: "Feijão — 20/11/2026 (Área principal) · colheita encerrada em 22/11/2026". */
export function plantingLabel(item: PlantingItem): string {
  const base = `${item.subject || "Plantio"} — ${formatDatePtBr(item.performedOn)}`;
  const plot = item.plot?.name ? ` (${item.plot.name})` : "";
  if (!isPlantingFinished(item)) return `${base}${plot}`;
  const finishedOn = item.planting?.finishedOn;
  const finished = finishedOn
    ? `colheita encerrada em ${formatDatePtBr(finishedOn)}`
    : "colheita encerrada";
  return `${base}${plot} · ${finished}`;
}

/** Produto da saída: cultura do catálogo ou nome digitado. */
export interface OutflowProduct {
  cropId: string;
  otherName: string;
}

interface CropOption {
  id?: string;
  active?: boolean;
}

/** Produto do plantio. Cultura fora do catálogo ativo vira nome digitado, porque a API recusaria o id. */
export function productFromPlanting(
  item: PlantingItem,
  crops: readonly CropOption[],
): OutflowProduct {
  const cropId = item.planting?.cropId;
  if (cropId && crops.some((crop) => crop.id === cropId && crop.active !== false)) {
    return { cropId, otherName: "" };
  }
  return { cropId: "", otherName: item.planting?.cropOtherName ?? item.subject ?? "" };
}

/**
 * Produto da saída depois de trocar o plantio (RN42): sugere o do plantio escolhido, mas mantém o
 * que a pessoa escolheu por conta própria.
 */
export function suggestOutflowProduct(
  current: OutflowProduct,
  previous: PlantingItem | undefined,
  next: PlantingItem | undefined,
  crops: readonly CropOption[],
): OutflowProduct {
  if (!next) return current;
  const isEmpty = !current.cropId && !current.otherName.trim();
  const cameFromPrevious =
    previous !== undefined && isSameProduct(current, productFromPlanting(previous, crops));
  return isEmpty || cameFromPrevious ? productFromPlanting(next, crops) : current;
}

function isSameProduct(a: OutflowProduct, b: OutflowProduct): boolean {
  return a.cropId === b.cropId && a.otherName.trim() === b.otherName.trim();
}
