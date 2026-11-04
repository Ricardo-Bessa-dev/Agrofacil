// Endereço no contrato, usado pelo agricultor e pela propriedade (doc 1 §3.2.3).
package br.edu.ifba.agrofacil.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param postalCode CEP com 8 dígitos, sem traço
 * @param uf sigla do estado, ex.: {@code BA}
 * @param municipalityId código IBGE
 * @param municipalityName preenchido pelo servidor
 * @param locality bairro, distrito ou povoado
 */
public record Address(
    @Pattern(regexp = "\\d{8}") String postalCode,
    @Pattern(regexp = "[A-Z]{2}") String uf,
    Integer municipalityId,
    @Schema(accessMode = Schema.AccessMode.READ_ONLY) String municipalityName,
    @Size(max = 120) String locality,
    @Size(max = 120) String street,
    @Size(max = 20) String number,
    @Size(max = 120) String complement) {

  /** Endereço sem nenhum campo preenchido. */
  public static Address empty() {
    return new Address(null, null, null, null, null, null, null, null);
  }
}
