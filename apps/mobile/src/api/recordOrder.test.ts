// Testa a ordem dos registros: pela criação, inclusive dos feitos sem internet, e pela data do manejo.
import { describe, expect, it } from "vitest";
import { compareRecords, SORT_BY_CREATION, type RecordDates } from "./recordOrder";

const record = (id: string, dates: RecordDates) => ({ id, ...dates });
const order = (records: ReturnType<typeof record>[], sort: string | null) =>
  [...records].sort(compareRecords(sort)).map((r) => r.id);

describe("compareRecords", () => {
  it("põe primeiro o registro criado por último, mesmo com data de manejo antiga", () => {
    const records = [
      record("antigo", { performedOn: "2026-11-22", createdAt: "2026-11-22T10:00:00Z" }),
      record("novo", { performedOn: "2026-11-01", createdAt: "2026-11-22T12:00:00Z" }),
    ];

    expect(order(records, SORT_BY_CREATION)).toEqual(["novo", "antigo"]);
  });

  it("separa pela hora os registros feitos no mesmo dia", () => {
    const day = { performedOn: "2026-11-22" };
    const records = [
      record("10h", { ...day, createdAt: "2026-11-22T10:00:00Z" }),
      record("12h", { ...day, createdAt: "2026-11-22T12:00:00Z" }),
      record("11h", { ...day, createdAt: "2026-11-22T11:00:00.123456Z" }),
    ];

    expect(order(records, SORT_BY_CREATION)).toEqual(["12h", "11h", "10h"]);
  });

  it("usa a hora do aparelho no registro que chegou à API mais tarde", () => {
    const records = [
      record("sem-internet", {
        clientCreatedAt: "2026-11-22T08:00:00Z",
        createdAt: "2026-11-22T18:00:00Z",
      }),
      record("com-internet", { createdAt: "2026-11-22T12:00:00Z" }),
    ];

    expect(order(records, SORT_BY_CREATION)).toEqual(["com-internet", "sem-internet"]);
  });

  it("põe no topo o registro que ainda está só no aparelho", () => {
    const records = [
      record("enviado", { createdAt: "2026-11-22T12:00:00Z" }),
      record("na-fila", { clientCreatedAt: "2026-11-22T13:00:00Z" }),
    ];

    expect(order(records, SORT_BY_CREATION)).toEqual(["na-fila", "enviado"]);
  });

  it("não sobe o registro antigo só porque foi editado", () => {
    const records = [
      record("editado", {
        createdAt: "2026-11-20T10:00:00Z",
        clientCreatedAt: "2026-11-22T15:00:00Z",
      }),
      record("recente", { createdAt: "2026-11-21T10:00:00Z" }),
    ];

    expect(order(records, SORT_BY_CREATION)).toEqual(["recente", "editado"]);
  });

  it("ordena pela data do manejo quando a criação não é pedida", () => {
    const records = [
      record("antigo", { performedOn: "2026-11-01", createdAt: "2026-11-22T12:00:00Z" }),
      record("novo", { performedOn: "2026-11-22", createdAt: "2026-11-22T10:00:00Z" }),
    ];

    expect(order(records, "performedOn,desc")).toEqual(["novo", "antigo"]);
  });

  it("deixa por último o registro sem data de criação", () => {
    const records = [record("sem-data", {}), record("com-data", { createdAt: "2026-11-22T10:00:00Z" })];

    expect(order(records, SORT_BY_CREATION)).toEqual(["com-data", "sem-data"]);
  });
});
