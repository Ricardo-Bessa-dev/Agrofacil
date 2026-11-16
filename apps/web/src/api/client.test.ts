// Testes do cliente HTTP e tratamento de Problem Details (RFC 9457) no apps/web.
import { describe, expect, it } from "vitest";
import { ApiProblemError, getAccessToken, setAccessToken } from "./client";

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
    expect(err.message).toBe("Verifique os campos.");
    expect(err.fieldErrors).toHaveLength(1);
    expect(err.fieldErrors[0]?.field).toBe("cpf");
  });
});
