// Testa a busca de todas as páginas: junta o conteúdo, respeita o total de páginas e propaga erros da API.
import { describe, expect, it, vi } from "vitest";
import { fetchAllPages, MAX_PAGE_SIZE } from "./pagination";

const okPage = (content: number[], totalPages: number) =>
  Promise.resolve({
    data: { content, page: { totalPages } },
    response: new Response(null, { status: 200 }),
  });

describe("fetchAllPages", () => {
  it("busca página por página até a última e junta tudo", async () => {
    const fetchPage = vi.fn(({ page }: { page: number }) =>
      okPage(page === 0 ? [1, 2] : page === 1 ? [3, 4] : [5], 3),
    );
    await expect(fetchAllPages(fetchPage)).resolves.toEqual([1, 2, 3, 4, 5]);
    expect(fetchPage).toHaveBeenCalledTimes(3);
    expect(fetchPage).toHaveBeenNthCalledWith(1, { page: 0, size: MAX_PAGE_SIZE });
  });

  it("faz uma chamada só quando há uma página (ou a API não informa o total)", async () => {
    const fetchPage = vi.fn(() =>
      Promise.resolve({ data: { content: [1] }, response: new Response(null, { status: 200 }) }),
    );
    await expect(fetchAllPages(fetchPage)).resolves.toEqual([1]);
    expect(fetchPage).toHaveBeenCalledTimes(1);
  });

  it("propaga o erro da API", async () => {
    const fetchPage = () =>
      Promise.resolve({
        data: undefined,
        error: { detail: "Sem permissão." },
        response: new Response(null, { status: 403 }),
      });
    await expect(fetchAllPages(fetchPage)).rejects.toThrow("Sem permissão.");
  });
});
