// Ordena as visitas do SPG: as marcadas, da mais próxima em diante; depois as passadas, da mais recente para trás.
import type { VisitView } from "@agrofacil/api-types";

/** Data que vale para a visita: a de realização, se já aconteceu, ou a marcada. */
export function visitDate(visit: VisitView): string {
  return visit.result?.doneOn ?? visit.plannedOn ?? "";
}

export function sortVisits(visits: readonly VisitView[]): VisitView[] {
  const planned = visits.filter((visit) => visit.status === "PLANNED");
  const past = visits.filter((visit) => visit.status !== "PLANNED");
  return [
    ...planned.sort((a, b) => visitDate(a).localeCompare(visitDate(b))),
    ...past.sort((a, b) => visitDate(b).localeCompare(visitDate(a))),
  ];
}
