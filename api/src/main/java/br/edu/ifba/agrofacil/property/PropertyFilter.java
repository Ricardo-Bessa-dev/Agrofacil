// Filtros da lista de propriedades do painel (doc 1 §3.8).
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.certification.CertificationStatus;
import java.util.UUID;

/**
 * @param certificationStatus status da certificação atual; {@code null} = todas
 */
public record PropertyFilter(
    UUID farmerId,
    String search,
    Integer municipalityId,
    CertificationStatus certificationStatus) {}
