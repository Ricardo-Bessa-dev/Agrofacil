// Busca todas as páginas de uma listagem paginada da API, para menus de seleção que precisam da lista inteira.
import { unwrap } from "./client";

/** Maior página aceita pela API (spring.data.web.pageable.max-page-size, doc 1 §3.2.4). */
export const MAX_PAGE_SIZE = 100;
/** Teto de páginas por busca (10 mil itens): evita laço infinito se a API responder errado. */
const MAX_PAGES = 100;

type PageResult<T> = { content?: T[]; page?: { totalPages?: number } };
type Pageable = { page: number; size: number };

/**
 * Chama fetchPage para cada página até a última e junta o conteúdo.
 * @param fetchPage recebe { page, size } e devolve a chamada do openapi-fetch (sem unwrap)
 */
export async function fetchAllPages<T>(
  fetchPage: (
    pageable: Pageable,
  ) => Promise<{ data?: PageResult<T>; error?: unknown; response: Response }>,
): Promise<T[]> {
  const items: T[] = [];
  for (let page = 0; page < MAX_PAGES; page++) {
    const result = await unwrap(fetchPage({ page, size: MAX_PAGE_SIZE }));
    items.push(...(result.content ?? []));
    const totalPages = result.page?.totalPages ?? 1;
    if (page + 1 >= totalPages) break;
  }
  return items;
}
