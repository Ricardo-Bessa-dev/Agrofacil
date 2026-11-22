// Testa a ordem das visitas do SPG na lista do app.
import { describe, expect, it } from "vitest";
import { sortVisits } from "./visitOrder";

describe("sortVisits", () => {
  it("põe as marcadas primeiro, da mais próxima em diante, e depois as passadas mais recentes", () => {
    const visits = [
      { id: "feita-antiga", status: "DONE" as const, plannedOn: "2026-03-01" },
      { id: "marcada-longe", status: "PLANNED" as const, plannedOn: "2026-12-20" },
      { id: "cancelada", status: "CANCELLED" as const, plannedOn: "2026-06-01" },
      { id: "marcada-perto", status: "PLANNED" as const, plannedOn: "2026-11-25" },
    ];

    expect(sortVisits(visits).map((v) => v.id)).toEqual([
      "marcada-perto",
      "marcada-longe",
      "cancelada",
      "feita-antiga",
    ]);
  });

  it("usa a data em que a visita aconteceu, e não a marcada", () => {
    const visits = [
      { id: "a", status: "DONE" as const, plannedOn: "2026-05-10" },
      { id: "b", status: "DONE" as const, plannedOn: "2026-05-01", result: { doneOn: "2026-05-20" } },
    ];

    expect(sortVisits(visits).map((v) => v.id)).toEqual(["b", "a"]);
  });

  it("não altera a lista recebida", () => {
    const visits = [
      { id: "2", status: "PLANNED" as const, plannedOn: "2026-12-20" },
      { id: "1", status: "PLANNED" as const, plannedOn: "2026-11-25" },
    ];

    sortVisits(visits);

    expect(visits.map((v) => v.id)).toEqual(["2", "1"]);
  });
});
