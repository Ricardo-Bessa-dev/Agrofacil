// Ponto do mapa de propriedades do painel (doc 1 §3.8).
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.certification.CertificationStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param certificationStatus {@code null} quando não há certificação atual
 */
public record PropertyMapItem(
    UUID id,
    String name,
    String farmerName,
    BigDecimal lat,
    BigDecimal lng,
    CertificationStatus certificationStatus) {}
