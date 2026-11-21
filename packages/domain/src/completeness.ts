// Regras de completude dos registros de manejo (doc 1 §2.5, ADR-06).

export type RecordCompletenessInput = {
  type: "PLANTING" | "INPUT_APPLICATION" | "HARVEST" | "INPUT_PURCHASE" | "OUTFLOW";
  plantedArea?: { value?: number; unit?: string } | null;
  seedOrigin?: string | null;
  expectedHarvestOn?: string | null;
  quantity?: { value?: number; unit?: string } | null;
  purpose?: string | null;
  supplier?: string | null;
  hasPhoto?: boolean;
  destination?: string | null;
};

/** Calcula os campos recomendados que ainda estão vazios para um registro de manejo. */
export function missingFields(record: RecordCompletenessInput): string[] {
  const hasValidQuantity = Boolean(
    record.quantity && record.quantity.value && record.quantity.value > 0,
  );
  const hasValidPlantedArea = Boolean(
    record.plantedArea && record.plantedArea.value && record.plantedArea.value > 0,
  );

  switch (record.type) {
    case "PLANTING":
      return [
        !hasValidPlantedArea && "plantedArea",
        !record.seedOrigin && "seedOrigin",
        !record.expectedHarvestOn && "expectedHarvestOn",
      ].filter(Boolean) as string[];
    case "INPUT_APPLICATION":
      return [!hasValidQuantity && "quantity", !record.purpose && "purpose"].filter(
        Boolean,
      ) as string[];
    case "HARVEST":
      return [!hasValidQuantity && "quantity"].filter(Boolean) as string[];
    case "INPUT_PURCHASE":
      return [
        !hasValidQuantity && "quantity",
        !record.supplier?.trim() && !record.hasPhoto && "supplierOrReceipt",
      ].filter(Boolean) as string[];
    case "OUTFLOW":
      return [!hasValidQuantity && "quantity", !record.destination && "destination"].filter(
        Boolean,
      ) as string[];
    default:
      return [];
  }
}

/** Indica se um registro tem todos os campos recomendados preenchidos. */
export function isRecordComplete(record: RecordCompletenessInput): boolean {
  return missingFields(record).length === 0;
}
