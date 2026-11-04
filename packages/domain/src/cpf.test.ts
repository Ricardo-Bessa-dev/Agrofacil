// Testes das funções de CPF (RN02).
import { describe, expect, it } from "vitest";
import { formatCpf, isValidCpf, maskCpf, normalizeCpf } from "./cpf";

describe("cpf", () => {
  it("tira a máscara", () => {
    expect(normalizeCpf("529.982.247-25")).toBe("52998224725");
  });

  it("aceita CPF válido com e sem máscara", () => {
    expect(isValidCpf("529.982.247-25")).toBe(true);
    expect(isValidCpf("52998224725")).toBe(true);
  });

  it("recusa dígito verificador errado, tamanho errado e sequência repetida", () => {
    expect(isValidCpf("529.982.247-26")).toBe(false);
    expect(isValidCpf("5299822472")).toBe(false);
    expect(isValidCpf("111.111.111-11")).toBe(false);
  });

  it("formata e mascara", () => {
    expect(formatCpf("52998224725")).toBe("529.982.247-25");
    expect(maskCpf("52998224725")).toBe("***.982.247-**");
  });
});
