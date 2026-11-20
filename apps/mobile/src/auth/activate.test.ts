import { describe, it, expect } from "vitest";
import { formatCpf, isValidCpf, onlyDigits } from "@agrofacil/domain";

describe("Ativação do Agricultor - Validações", () => {
  it("valida CPF corretamente", () => {
    // CPF com formato inválido
    expect(isValidCpf("123")).toBe(false);
    expect(isValidCpf("11111111111")).toBe(false);

    // Formata e extrai apenas dígitos
    const formatted = formatCpf("52998224725");
    expect(formatted).toBe("529.982.247-25");
    expect(onlyDigits(formatted)).toBe("52998224725");
    expect(isValidCpf("52998224725")).toBe(true);
  });

  it("valida que o código de acesso possui 6 dígitos", () => {
    const validCode = "123456";
    const invalidShort = "123";
    const invalidLong = "1234567";

    expect(validCode.trim().length).toBe(6);
    expect(/^\d{6}$/.test(validCode)).toBe(true);
    expect(/^\d{6}$/.test(invalidShort)).toBe(false);
    expect(/^\d{6}$/.test(invalidLong)).toBe(false);
  });
});
