// Testa o gerador de UUID, inclusive a alternativa usada quando crypto.randomUUID não existe.
import { afterEach, describe, expect, it, vi } from "vitest";
import { generateUuid, uuidFromRandomBytes } from "./uuid";

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe("generateUuid", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("gera UUID v4 com crypto.randomUUID", () => {
    expect(generateUuid()).toMatch(UUID_V4);
  });

  it("gera UUID v4 diferentes sem crypto.randomUUID (HTTP fora de contexto seguro)", () => {
    // Simula o crypto de uma página aberta por HTTP: só getRandomValues.
    const realCrypto = globalThis.crypto;
    vi.stubGlobal("crypto", { getRandomValues: realCrypto.getRandomValues.bind(realCrypto) });
    const a = generateUuid();
    const b = generateUuid();
    expect(a).toMatch(UUID_V4);
    expect(b).toMatch(UUID_V4);
    expect(a).not.toBe(b);
  });

  it("marca versão e variante nos bytes certos", () => {
    expect(uuidFromRandomBytes(new Uint8Array(16).fill(0xff))).toBe(
      "ffffffff-ffff-4fff-bfff-ffffffffffff",
    );
    expect(uuidFromRandomBytes(new Uint8Array(16))).toBe("00000000-0000-4000-8000-000000000000");
  });
});
