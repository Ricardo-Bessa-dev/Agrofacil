// Testa a identificação dos registros que ainda estão só na fila offline do aparelho.
import { describe, expect, it } from "vitest";
import type { QueuedMutation } from "./client";
import { pendingRecordIds } from "./pendingRecords";

const mutation = (entity: QueuedMutation["entity"], entityId: string): QueuedMutation => ({
  mutationId: `m-${entityId}`,
  entity,
  op: "UPSERT",
  entityId,
  clientTimestamp: "2026-11-21T10:00:00Z",
});

describe("pendingRecordIds", () => {
  it("lista só os registros da fila, sem repetir", () => {
    const queue = [mutation("record", "r1"), mutation("property", "p1"), mutation("record", "r1")];
    expect([...pendingRecordIds(queue)]).toEqual(["r1"]);
  });

  it("fica vazio com a fila vazia", () => {
    expect(pendingRecordIds([]).size).toBe(0);
  });
});
