// Filtros da lista de certificações do painel (doc 1 §3.10).
package br.edu.ifba.agrofacil.certification;

import java.util.UUID;

/**
 * @param expiringWithinDays só as em vigor que vencem em até N dias
 * @param currentOnly só a certificação atual de cada propriedade (padrão: sim)
 */
public record CertificationFilter(
    CertificationStatus status,
    Integer expiringWithinDays,
    UUID farmerId,
    UUID propertyId,
    Boolean currentOnly) {

  public boolean onlyCurrent() {
    return currentOnly == null || currentOnly;
  }
}
