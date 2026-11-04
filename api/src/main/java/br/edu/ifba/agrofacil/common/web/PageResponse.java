// Página de resultados no formato do contrato: { content, page } (doc 1 §3.2.4).
package br.edu.ifba.agrofacil.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Independe do formato interno do Spring, que pode mudar entre versões. */
public record PageResponse<T>(List<T> content, PageMeta page) {

  /**
   * @param number página atual, começando em 0
   * @param size tamanho da página
   * @param totalElements total de itens em todas as páginas
   * @param totalPages total de páginas
   */
  public record PageMeta(int number, int size, long totalElements, int totalPages) {}

  /** Converte uma página do Spring, aplicando {@code mapper} a cada item. */
  public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
    return new PageResponse<>(
        page.getContent().stream().map(mapper).toList(),
        new PageMeta(
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
  }

  /** Página já com os itens convertidos (útil quando a conversão é feita em lote). */
  public static <T> PageResponse<T> of(Page<?> page, List<T> content) {
    return new PageResponse<>(
        content,
        new PageMeta(
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
  }
}
