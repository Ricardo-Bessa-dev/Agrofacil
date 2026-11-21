// Barra "Página X de Y" com Anterior/Próxima para as listagens paginadas da API.
import type { PageMeta } from "@agrofacil/api-types";

type PaginationProps = {
  /** Metadados da página devolvidos pela API (page.number começa em 0). */
  meta: PageMeta | undefined;
  onPageChange: (page: number) => void;
  /** Nome dos itens no plural, ex.: "registros", "visitas". */
  itemsLabel: string;
};

/** Não aparece quando tudo cabe numa página só. */
export function Pagination({ meta, onPageChange, itemsLabel }: PaginationProps) {
  const totalPages = meta?.totalPages ?? 1;
  if (!meta || totalPages <= 1) return null;
  const current = meta.number ?? 0;
  return (
    <nav
      className="af-pagination"
      aria-label={`Páginas de ${itemsLabel}`}
      style={{ marginTop: 16 }}
    >
      <span>
        Página {current + 1} de {totalPages} ({meta.totalElements} {itemsLabel})
      </span>
      <div style={{ display: "flex", gap: 8 }}>
        <button
          type="button"
          className="af-btn af-btn--outline af-btn--sm"
          disabled={current <= 0}
          onClick={() => onPageChange(Math.max(0, current - 1))}
        >
          Anterior
        </button>
        <button
          type="button"
          className="af-btn af-btn--outline af-btn--sm"
          disabled={current + 1 >= totalPages}
          onClick={() => onPageChange(current + 1)}
        >
          Próxima
        </button>
      </div>
    </nav>
  );
}
