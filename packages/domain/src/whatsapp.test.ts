// Testa a montagem do número e do link do WhatsApp a partir dos formatos de telefone usados no sistema.
import { describe, expect, it } from "vitest";
import { whatsappLink, whatsappNumber } from "./whatsapp";

describe("whatsappNumber", () => {
  it("usa o E.164 da API sem repetir o 55", () => {
    expect(whatsappNumber("+5571999998888")).toBe("5571999998888");
  });

  it("acrescenta o 55 em números nacionais com DDD", () => {
    expect(whatsappNumber("(71) 99999-8888")).toBe("5571999998888");
    expect(whatsappNumber("71 3333-4444")).toBe("557133334444");
  });

  it("mantém números já com 55 sem o +", () => {
    expect(whatsappNumber("5571999998888")).toBe("5571999998888");
  });

  it("devolve null para telefone vazio ou curto demais", () => {
    expect(whatsappNumber("")).toBeNull();
    expect(whatsappNumber(null)).toBeNull();
    expect(whatsappNumber("9999-8888")).toBeNull();
  });
});

describe("whatsappLink", () => {
  it("monta o link com a mensagem codificada", () => {
    expect(whatsappLink("+5571999998888", "Olá, José!")).toBe(
      "https://wa.me/5571999998888?text=Ol%C3%A1%2C%20Jos%C3%A9!",
    );
  });

  it("monta o link sem mensagem e devolve null sem telefone", () => {
    expect(whatsappLink("+5571999998888")).toBe("https://wa.me/5571999998888");
    expect(whatsappLink(undefined)).toBeNull();
  });
});
