// Ordena os registros de manejo do espelho local: do mais novo para o mais antigo.

/** Datas do registro usadas na ordenação (ISO 8601). */
export interface RecordDates {
  performedOn?: string;
  /** Quando a API recebeu o registro; ausente enquanto ele está só no aparelho. */
  createdAt?: string;
  /** Quando o registro foi feito no aparelho; ausente nos criados pelo painel. */
  clientCreatedAt?: string;
}

/** Valor de `sort` que pede a ordem de criação. */
export const SORT_BY_CREATION = "createdAt,desc";

/**
 * Momento em que o registro foi criado, em milissegundos. Vale a data do aparelho, para um registro
 * feito sem internet manter o lugar dele depois do envio. Ela nunca é posterior à da API: se for, foi
 * gravada numa edição ou o relógio do aparelho está errado, e vale a da API.
 */
export function createdAtMillis({ createdAt, clientCreatedAt }: RecordDates): number {
  const times = [createdAt, clientCreatedAt]
    .map((value) => (value ? Date.parse(value) : Number.NaN))
    .filter((time) => !Number.isNaN(time));
  return times.length > 0 ? Math.min(...times) : 0;
}

/** Mais novo primeiro, pela criação ou pela data do manejo; no empate, vale o outro critério. */
export function compareRecords(sort: string | null): (a: RecordDates, b: RecordDates) => number {
  const byCreation = (a: RecordDates, b: RecordDates) => createdAtMillis(b) - createdAtMillis(a);
  const byDate = (a: RecordDates, b: RecordDates) =>
    (b.performedOn ?? "").localeCompare(a.performedOn ?? "");
  return sort === SORT_BY_CREATION
    ? (a, b) => byCreation(a, b) || byDate(a, b)
    : (a, b) => byDate(a, b) || byCreation(a, b);
}
