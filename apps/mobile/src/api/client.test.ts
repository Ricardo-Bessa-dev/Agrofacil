import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import {
  ApiError,
  clearSession,
  discardRejectedMutation,
  fetchSyncSnapshot,
  flushOfflineQueue,
  invalidateMobileSyncCache,
  submitMutation,
  type QueuedMutation,
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

describe("sincronização do agricultor", () => {
  let fetchApi: ReturnType<typeof vi.fn<(url: string, init?: RequestInit) => Promise<Response>>>;

  beforeEach(() => {
    clearSession();
    localStorage.clear();
    fetchApi = vi.fn(async () => Response.json({}));
    vi.stubGlobal("window", {
      location: { origin: "https://agrofacil.example.test" },
      fetch: fetchApi,
      dispatchEvent: () => true,
    });
    vi.stubGlobal("navigator", { onLine: false });
    saveSession({ accessToken: "a-token", refreshToken: "a-refresh", user: { id: "a" } });
  });

  afterEach(() => vi.unstubAllGlobals());

  it("envia lotes de até 100 e conserva itens recusados e os novos itens da fila", async () => {
    for (let i = 0; i < 101; i++) {
      await submitMutation("record", `r-${i}`, { type: "PLANTING" });
    }
    const calls: number[] = [];
    fetchApi.mockImplementation(async (_url, init) => {
      const { mutations } = JSON.parse(String(init?.body)) as { mutations: QueuedMutation[] };
      calls.push(mutations.length);
      if (calls.length === 1) {
        await submitMutation("record", "nova", { type: "PLANTING" });
      }
      return Response.json({
        results: mutations.map(({ mutationId, entityId }) => ({
          mutationId,
          status: entityId === "r-0" ? "REJECTED" : "APPLIED",
          error: { message: "Corrija a data." },
        })),
      });
    });

    expect(await flushOfflineQueue()).toEqual({ synced: 101, remaining: 1 });
    expect(calls).toEqual([100, 2]);
    expect(getOfflineQueue()).toMatchObject([{ entityId: "r-0", rejection: "Corrija a data." }]);
  });

  it("aplica páginas, exclusões e reinício sem compartilhar dados entre contas", async () => {
    vi.stubGlobal("navigator", { onLine: true });
    fetchApi
      .mockResolvedValueOnce(
        Response.json({
          cursor: "p1",
          hasMore: true,
          changes: {
            plots: [{ id: "remover" }],
            units: [{ code: "KILOGRAM", namePlural: "quilos" }],
          },
        }),
      )
      .mockResolvedValueOnce(
        Response.json({
          cursor: "p2",
          hasMore: false,
          changes: { plots: [{ id: "manter" }] },
          deleted: [{ entity: "plot", id: "remover" }],
        }),
      )
      .mockResolvedValueOnce(Response.json({ reset: true }))
      .mockResolvedValueOnce(Response.json({ cursor: "p3", changes: { plots: [{ id: "novo" }] } }));

    expect((await fetchSyncSnapshot()).changes?.plots).toEqual([{ id: "manter" }]);
    expect(fetchApi.mock.calls[1]?.[0]).toContain("cursor=p1");
    invalidateMobileSyncCache();
    expect((await fetchSyncSnapshot()).changes?.plots).toEqual([{ id: "novo" }]);
    expect(fetchApi.mock.calls[3]?.[0]).toContain("cursor=0");
    vi.stubGlobal("navigator", { onLine: false });
    await submitMutation("record", "privado", { type: "PLANTING" });
    clearSession();
    saveSession({ accessToken: "b-token", user: { id: "b" } });
    expect(getOfflineQueue()).toEqual([]);
    fetchApi.mockRejectedValueOnce(new TypeError("sem conexão"));
    await expect(fetchSyncSnapshot()).rejects.toThrow("sem conexão");
    clearSession();
    saveSession({ accessToken: "a-token", user: { id: "a" } });
    expect(getOfflineQueue()).toMatchObject([{ entityId: "privado" }]);
  });

  it("não remove sessão quando a renovação falha por perda de conexão", async () => {
    vi.stubGlobal("navigator", { onLine: true });
    fetchApi
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockRejectedValueOnce(new TypeError("sem conexão"));
    await expect(fetchSyncSnapshot()).rejects.toThrow();
    expect(getStoredUser()?.id).toBe("a");
    expect(getStoredRefreshToken()).toBe("a-refresh");
  });

  it("usa o espelho salvo quando o token vence e não há rede para renovar", async () => {
    vi.stubGlobal("navigator", { onLine: true });
    fetchApi.mockResolvedValueOnce(
      Response.json({ cursor: "c1", changes: { plots: [{ id: "salvo" }] } }),
    );
    await fetchSyncSnapshot();
    invalidateMobileSyncCache();
    fetchApi
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockRejectedValueOnce(new TypeError("sem conexão"));

    expect((await fetchSyncSnapshot()).changes?.plots).toEqual([{ id: "salvo" }]);
  });

  it("guarda na fila o registro salvo com o token vencido e sem rede para renovar", async () => {
    vi.stubGlobal("navigator", { onLine: true });
    fetchApi
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockRejectedValueOnce(new TypeError("sem conexão"));

    expect(await submitMutation("record", "r-1", { type: "PLANTING" })).toBe("queued");
    expect(getOfflineQueue()).toMatchObject([{ entityId: "r-1" }]);
  });

  it("apaga o espelho ao sair, para não deixar dados pessoais no aparelho (LGPD)", async () => {
    vi.stubGlobal("navigator", { onLine: true });
    fetchApi.mockResolvedValueOnce(
      Response.json({ cursor: "c1", changes: { plots: [{ id: "meu" }] } }),
    );
    await fetchSyncSnapshot();
    expect(localStorage.getItem("af_mobile_sync_state:a")).toContain("meu");

    clearSession();

    expect(localStorage.getItem("af_mobile_sync_state:a")).toBeNull();
  });

  it("recupera da fila antiga, sem dono, só o que é desta conta", async () => {
    vi.stubGlobal("navigator", { onLine: true });
    const legacy = (id: string, propertyId: string) => ({
      mutationId: `m-${id}`,
      entity: "record",
      op: "UPSERT",
      entityId: id,
      clientTimestamp: "2026-11-21T10:00:00Z",
      data: { propertyId },
    });
    localStorage.setItem(
      "af_mobile_offline_queue",
      JSON.stringify([legacy("meu", "p-a"), legacy("de-outra-conta", "p-b")]),
    );
    fetchApi.mockResolvedValueOnce(
      Response.json({ cursor: "c1", changes: { properties: [{ id: "p-a" }] } }),
    );

    await fetchSyncSnapshot();

    expect(getOfflineQueue().map((m) => m.entityId)).toEqual(["meu"]);
    expect(localStorage.getItem("af_mobile_offline_queue")).toContain("de-outra-conta");
  });

  it("permite descartar só o que foi recusado", async () => {
    await submitMutation("record", "recusado", { type: "PLANTING" });
    await submitMutation("record", "na-fila", { type: "PLANTING" });
    fetchApi.mockImplementation(async (_url, init) => {
      const { mutations } = JSON.parse(String(init?.body)) as { mutations: QueuedMutation[] };
      return Response.json({
        results: mutations
          .filter((m) => m.entityId === "recusado")
          .map((m) => ({ mutationId: m.mutationId, status: "REJECTED" })),
      });
    });
    await flushOfflineQueue();

    for (const item of getOfflineQueue()) discardRejectedMutation(item.mutationId);

    expect(getOfflineQueue().map((m) => m.entityId)).toEqual(["na-fila"]);
  });
});
