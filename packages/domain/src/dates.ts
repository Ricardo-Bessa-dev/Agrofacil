// Funções de formatação de datas para o padrão brasileiro (doc 3 §B.4 e §D.7).

/** Converte "2026-11-20" para "20/11/2026". */
export function formatDatePtBr(isoDate?: string | null): string {
  if (!isoDate) return "—";
  const clean = isoDate.slice(0, 10);
  const [year, month, day] = clean.split("-");
  if (!year || !month || !day) return isoDate;
  return `${day}/${month}/${year}`;
}

/** Converte um timestamp ISO para "20/11/2026 às 14:32". */
export function formatDateTimePtBr(isoDateTime?: string | null): string {
  if (!isoDateTime) return "—";
  const date = new Date(isoDateTime);
  if (Number.isNaN(date.getTime())) return isoDateTime;
  const d = new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
  return d.replace(", ", " às ");
}

/** Retorna a data de hoje no formato YYYY-MM-DD. */
export function todayIso(now: Date = new Date()): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/** Soma 1 ano a uma data no formato YYYY-MM-DD (RN18). */
export function addOneYearIso(isoDate: string): string {
  const clean = isoDate.slice(0, 10);
  const [y, m, d] = clean.split("-").map(Number);
  if (!y || !m || !d) return todayIso();
  const nextYear = y + 1;
  return `${nextYear}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
}
