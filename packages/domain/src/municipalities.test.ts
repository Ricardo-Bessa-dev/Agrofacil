// Testa a lista local dos municípios da Bahia (precisa bater com a migração V4 da API).
import { describe, expect, it } from "vitest";
import { BAHIA_MUNICIPALITIES } from "./municipalities";

describe("BAHIA_MUNICIPALITIES", () => {
  it("contém os 417 municípios da Bahia, de Abaíra a Xique-Xique", () => {
    expect(BAHIA_MUNICIPALITIES).toHaveLength(417);
    expect(BAHIA_MUNICIPALITIES[0]?.name).toBe("Abaíra");
    expect(BAHIA_MUNICIPALITIES[416]?.name).toBe("Xique-Xique");
  });

  it("não repete códigos IBGE", () => {
    expect(new Set(BAHIA_MUNICIPALITIES.map((m) => m.id)).size).toBe(417);
  });
});
