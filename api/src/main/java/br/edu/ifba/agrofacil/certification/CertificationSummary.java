// Resumo da certificação atual de uma propriedade (doc 1 §3.10).
package br.edu.ifba.agrofacil.certification;

import java.time.LocalDate;
import java.util.UUID;

/**
 * @param daysToExpire negativo se já venceu
 */
public record CertificationSummary(
    UUID id,
    CertificationStatus status,
    LocalDate issuedOn,
    LocalDate expiresOn,
    long daysToExpire) {}
