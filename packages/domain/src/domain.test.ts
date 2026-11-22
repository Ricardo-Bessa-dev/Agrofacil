// Testes unitários para as regras puras compartilhadas no pacote @agrofacil/domain.
import { describe, expect, it } from "vitest";
import { certificationBadgeInfo } from "./certification";
import { isRecordComplete, missingFields } from "./completeness";
import { formatDatePtBr } from "./dates";
import { formatArea, toSquareMeters } from "./units";

describe("domain rules", () => {
  it("converts area units to square meters", () => {
    expect(toSquareMeters(2, "TAREFA", 4356)).toBe(8712);
    expect(toSquareMeters(1.5, "HECTARE")).toBe(15000);
    expect(toSquareMeters(500, "SQUARE_METER")).toBe(500);
  });

  it("formats area in pt-BR", () => {
    expect(formatArea({ value: 12.5, unit: "HECTARE" })).toBe("12,5 hectares");
    expect(formatArea({ value: 1, unit: "TAREFA" })).toBe("1 tarefa");
  });

  it("calculates completeness and missing fields", () => {
    expect(missingFields({ type: "HARVEST" })).toEqual(["quantity"]);
    expect(isRecordComplete({ type: "HARVEST", quantity: { value: 30, unit: "BUNCH" } })).toBe(
      true,
    );
    expect(
      missingFields({
        type: "PLANTING",
        plantedArea: { value: 1, unit: "TAREFA" },
      }),
    ).toEqual(["seedOrigin", "expectedHarvestOn"]);
  });

  it("returns certification badge info", () => {
    const valid = certificationBadgeInfo("VALID", "2027-10-23");
    expect(valid.figmaLabel).toBe("Certificação regular");
    expect(valid.tone).toBe("success");

    const expired = certificationBadgeInfo("EXPIRED", "2026-10-23");
    expect(expired.figmaLabel).toBe("Certificação irregular");
    expect(expired.tone).toBe("danger");
  });

  it("formats ISO dates to pt-BR", () => {
    expect(formatDatePtBr("2026-09-01")).toBe("01/09/2026");
  });
});
