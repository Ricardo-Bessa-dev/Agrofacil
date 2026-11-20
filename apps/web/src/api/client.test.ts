// Testes do cliente HTTP, normalização E.164, municípios da Bahia e tratamento de Problem Details (RFC 9457) no apps/web.
import { describe, expect, it } from "vitest";
import { ApiProblemError, getAccessToken, normalizePhoneE164, setAccessToken } from "./client";
import { BAHIA_MUNICIPALITIES } from "./municipalities";

describe("apps/web api client", () => {
  it("armazena e limpa o accessToken em memória", () => {
    expect(getAccessToken()).toBeNull();
    setAccessToken("jwt-token-teste");
    expect(getAccessToken()).toBe("jwt-token-teste");
    setAccessToken(null);
    expect(getAccessToken()).toBeNull();
  });

  it("formata ApiProblemError com código, detalhe e erros por campo", () => {
    const err = new ApiProblemError(422, {
      title: "Dados inválidos",
      detail: "Verifique os campos.",
      code: "VALIDATION_ERROR",
      errors: [{ field: "cpf", code: "INVALID_CPF", message: "CPF inválido." }],
    });
    expect(err.status).toBe(422);
    expect(err.code).toBe("VALIDATION_ERROR");
    expect(err.message).toContain("Verifique os campos.");
    expect(err.message).toContain("cpf: CPF inválido.");
    expect(err.fieldErrors).toHaveLength(1);
    expect(err.fieldErrors[0]?.field).toBe("cpf");
  });

  it("normaliza telefones brasileiros para o formato E.164 (+55...)", () => {
    expect(normalizePhoneE164("")).toBeUndefined();
    expect(normalizePhoneE164("(74) 99999-8888")).toBe("+5574999998888");
    expect(normalizePhoneE164("74999998888")).toBe("+5574999998888");
    expect(normalizePhoneE164("+5574999998888")).toBe("+5574999998888");
  });

  it("contém todos os 417 municípios da Bahia de A a X", () => {
    expect(BAHIA_MUNICIPALITIES).toHaveLength(417);
    expect(BAHIA_MUNICIPALITIES[0]?.name).toBe("Abaíra");
    expect(BAHIA_MUNICIPALITIES[416]?.name).toBe("Xique-Xique");
  });
});
