// Formatos de escrita dos catálogos enviados pelo painel (doc 1 §3.13).
package br.edu.ifba.agrofacil.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class CatalogWrites {

  private CatalogWrites() {}

  /**
   * @param icon chave do ícone no app, ex.: {@code lettuce}
   * @param active nulo = ativo
   * @param version obrigatório só no PATCH
   */
  public record CropWrite(
      @NotBlank @Size(max = 80) String name,
      @Size(max = 40) String icon,
      UUID categoryId,
      Boolean active,
      Long version) {}

  /**
   * @param regulatoryReference ex.: "Portaria MAPA 52/2021, Anexo V"
   * @param needsAuthorization o OAC precisa autorizar o uso
   * @param homeMade feito na propriedade
   */
  public record FarmInputWrite(
      @NotBlank @Size(max = 120) String name,
      @NotNull InputType inputType,
      @Size(max = 200) String regulatoryReference,
      Boolean needsAuthorization,
      Boolean homeMade,
      Boolean active,
      Long version) {}

  /** Só a disponibilidade e a ordem da unidade mudam; campo ausente = não muda. */
  public record UnitWrite(Boolean active, @Min(0) @Max(9999) Integer sortOrder) {}

  /** Tipo de item sem catálogo (RN22). */
  public enum UnmappedKind {
    CROP,
    INPUT
  }

  /**
   * @param text o texto livre como o agricultor escreveu (comparado sem diferenciar maiúsculas)
   * @param targetId cultura ou insumo do catálogo que passa a valer para esses registros
   */
  public record ResolveUnmapped(
      @NotNull UnmappedKind kind, @NotBlank @Size(max = 200) String text, @NotNull UUID targetId) {}
}
