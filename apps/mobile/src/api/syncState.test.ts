// Testa a montagem do espelho local com o /sync/pull incremental: upsert, lápides, reset e formato salvo.
import { describe, expect, it } from "vitest";
import type { PullResponse } from "@agrofacil/api-types";
import {
  applyPull,
  emptySyncState,
  INITIAL_CURSOR,
  parseSyncState,
  type SyncChanges,
} from "./syncState";

/**
 * Itens do roteiro de visita. O tipo gerado do OpenAPI está errado (duas classes "View" na API colidem),
 * então o teste converte explicitamente.
 */
const checklist = (...items: { code: string; question: string }[]) =>
  items as unknown as SyncChanges["visitChecklistItems"];

/** Registro mínimo válido para o tipo (o contrato exige o type). */
const rec = (id: string, notes?: string) => ({ id, type: "PLANTING" as const, notes });

const page = (overrides: Partial<PullResponse>): PullResponse => ({
  cursor: "10.1700000000000",
  hasMore: false,
  reset: false,
  changes: {},
  deleted: [],
  ...overrides,
});

describe("applyPull", () => {
  it("acrescenta itens novos e guarda o cursor", () => {
    const state = applyPull(emptySyncState(), page({ changes: { records: [rec("r1", "a")] } }));
    expect(state.cursor).toBe("10.1700000000000");
    expect(state.changes.records).toEqual([rec("r1", "a")]);
  });

  it("substitui pela versão nova sem apagar os itens que não vieram", () => {
    const first = applyPull(
      emptySyncState(),
      page({ changes: { records: [rec("r1", "a"), rec("r2", "b")] } }),
    );
    const second = applyPull(
      first,
      page({ cursor: "11.1", changes: { records: [rec("r1", "editado")] } }),
    );
    expect(second.changes.records).toEqual([rec("r1", "editado"), rec("r2", "b")]);
  });

  it("remove os itens das lápides", () => {
    const first = applyPull(
      emptySyncState(),
      page({ changes: { records: [rec("r1")], attachments: [{ id: "a1" }] } }),
    );
    const second = applyPull(
      first,
      page({
        deleted: [
          { entity: "record", id: "r1" },
          { entity: "attachment", id: "a1" },
        ],
      }),
    );
    expect(second.changes.records).toEqual([]);
    expect(second.changes.attachments).toEqual([]);
  });

  it("usa o código como chave das unidades de medida", () => {
    const first = applyPull(
      emptySyncState(),
      page({ changes: { units: [{ code: "KG", nameSingular: "quilo" }] } }),
    );
    const second = applyPull(
      first,
      page({ changes: { units: [{ code: "KG", nameSingular: "quilograma" }] } }),
    );
    expect(second.changes.units).toEqual([{ code: "KG", nameSingular: "quilograma" }]);
  });

  it("usa o código como chave dos itens do roteiro de visita", () => {
    const first = applyPull(
      emptySyncState(),
      page({
        changes: {
          visitChecklistItems: checklist(
            { code: "A1", question: "?" },
            { code: "A2", question: "?" },
          ),
        },
      }),
    );
    const second = applyPull(
      first,
      page({ changes: { visitChecklistItems: checklist({ code: "A1", question: "Nova?" }) } }),
    );
    expect(second.changes.visitChecklistItems).toEqual([
      { code: "A1", question: "Nova?" },
      { code: "A2", question: "?" },
    ]);
  });

  it("mantém me e association quando a página não os traz", () => {
    const first = applyPull(emptySyncState(), page({ changes: { me: { name: "José" } } }));
    const second = applyPull(first, page({ changes: { records: [rec("r1")] } }));
    expect(second.changes.me).toEqual({ name: "José" });
  });

  it("zera o espelho quando a API pede reset", () => {
    const first = applyPull(emptySyncState(), page({ changes: { records: [rec("r1")] } }));
    const reset = applyPull(first, page({ reset: true, cursor: "0" }));
    expect(reset).toEqual(emptySyncState());
    expect(reset.cursor).toBe(INITIAL_CURSOR);
  });

  it("não altera o estado recebido", () => {
    const first = applyPull(emptySyncState(), page({ changes: { records: [rec("r1")] } }));
    applyPull(first, page({ changes: { records: [rec("r2")] } }));
    expect(first.changes.records).toEqual([rec("r1")]);
  });
});

describe("parseSyncState", () => {
  it("lê o estado salvo no formato atual", () => {
    const state = applyPull(emptySyncState(), page({ changes: { records: [rec("r1")] } }));
    expect(parseSyncState(JSON.stringify(state))).toEqual(state);
  });

  it("descarta o formato antigo (snapshot completo, sem versão), texto inválido e vazio", () => {
    expect(parseSyncState(JSON.stringify({ cursor: "5.1", changes: {} }))).toBeNull();
    expect(parseSyncState("{quebrado")).toBeNull();
    expect(parseSyncState(null)).toBeNull();
  });
});
