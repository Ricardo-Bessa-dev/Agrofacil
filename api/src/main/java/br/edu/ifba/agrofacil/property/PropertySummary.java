// Resumo de propriedade usado na ficha do agricultor (doc 1 §3.8).
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.certification.CertificationSummary;
import java.util.UUID;

public record PropertySummary(
    UUID id, String name, String municipalityName, CertificationSummary currentCertification) {}
