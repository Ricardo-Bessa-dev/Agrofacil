import { describe, it, expect, beforeEach } from "vitest";
import {
  ApiError,
  clearSession,
  getOfflineQueue,
  getStoredAccessToken,
  getStoredDeviceId,
  getStoredRefreshToken,
  getStoredUser,
  saveSession,
} from "./client";

const mockStore: Record<string, string> = {};

if (typeof globalThis.localStorage === "undefined") {
  (globalThis as unknown as { localStorage: Storage }).localStorage = {
    getItem: (key: string) => mockStore[key] ?? null,
    setItem: (key: string, val: string) => {
      mockStore[key] = String(val);
    },
    removeItem: (key: string) => {
      delete mockStore[key];
    },
    clear: () => {
      for (const k in mockStore) delete mockStore[k];
    },
    length: 0,
    key: () => null,
  };
}

describe("Mobile API Client & Storage", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("gera e persiste um deviceId único quando não existir", () => {
    const id1 = getStoredDeviceId();
    expect(id1).toBeDefined();
    expect(typeof id1).toBe("string");
    expect(id1.length).toBeGreaterThan(10);

    const id2 = getStoredDeviceId();
    expect(id2).toBe(id1);
  });

  it("salva tokens e dados do agricultor na sessão", () => {
    saveSession({
      accessToken: "access-token-123",
      refreshToken: "refresh-token-456",
      user: {
        id: "farmer-uuid-1",
        name: "José Silva",
        cpf: "12345678909",
        role: "FARMER",
        farmer: {
          cafNumber: "BA1234567",
        },
      },
    });

    expect(getStoredAccessToken()).toBe("access-token-123");
    expect(getStoredRefreshToken()).toBe("refresh-token-456");
    const user = getStoredUser();
    expect(user?.name).toBe("José Silva");
    expect(user?.cpf).toBe("12345678909");
    expect(user?.farmer?.cafNumber).toBe("BA1234567");
  });

  it("limpa sessão no logout", () => {
    saveSession({
      accessToken: "token-abc",
      refreshToken: "refresh-xyz",
      user: { id: "u-1", name: "Maria" },
    });

    expect(getStoredAccessToken()).toBe("token-abc");
    clearSession();
    expect(getStoredAccessToken()).toBeNull();
    expect(getStoredRefreshToken()).toBeNull();
    expect(getStoredUser()).toBeNull();
  });

  it("inicia a fila offline vazia e formata erros de validação detalhados", () => {
    expect(getOfflineQueue()).toEqual([]);
    const err = new ApiError(422, {
      detail: "Alguns campos precisam ser corrigidos.",
      code: "VALIDATION_ERROR",
      errors: [{ field: "plantingId", message: "Escolha o plantio." }],
    });
    expect(err.message).toContain("plantingId: Escolha o plantio.");
  });
});
