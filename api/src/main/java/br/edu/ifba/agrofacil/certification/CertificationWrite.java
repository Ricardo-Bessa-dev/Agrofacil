// Dados de uma certificação enviados pelo painel: criação, renovação e edição (doc 1 §3.10).
package br.edu.ifba.agrofacil.certification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * As regras que dependem do banco (propriedade, cultura e trabalhadores no escopo) e das datas
 * (RN18) ficam no {@link CertificationService}.
 *
 * @param propertyId obrigatório na criação; na renovação vem da certificação anterior
 * @param expiresOn se omitido: {@code issuedOn} + 1 ano (RN18)
 * @param products ao menos 1; na renovação, vazio = copia da anterior
 * @param workerIds trabalhadores da mesma propriedade; na renovação, vazio = copia da anterior
 * @param version obrigatório só na edição (PUT)
 */
public record CertificationWrite(
    UUID propertyId,
    @Size(max = 60) String number,
    @NotNull LocalDate issuedOn,
    LocalDate expiresOn,
    @Size(max = 200) String scope,
    @Valid @Size(max = 100) List<Product> products,
    @Size(max = 200) List<@NotNull UUID> workerIds,
    @Size(max = 2000) String notes,
    Long version) {

  /**
   * @param cropId cultura do catálogo
   * @param productName nome livre, quando a cultura não está no catálogo
   */
  public record Product(UUID cropId, @Size(max = 120) String productName) {}

  public List<Product> productsOrEmpty() {
    return products == null ? List.of() : products;
  }

  public List<UUID> workerIdsOrEmpty() {
    return workerIds == null ? List.of() : workerIds;
  }
}
