// Propriedade no formato do contrato (doc 1 §3.8), com o nome do agricultor para o painel.
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.certification.CertificationSummary;
import br.edu.ifba.agrofacil.common.domain.Address;
import br.edu.ifba.agrofacil.common.domain.Area;
import br.edu.ifba.agrofacil.common.domain.GeoPoint;
import java.time.Instant;
import java.util.UUID;

public record PropertyView(
    UUID id,
    UUID farmerId,
    String farmerName,
    String name,
    Area totalArea,
    String productionDescription,
    GeoPoint location,
    Address address,
    String accessDirections,
    CertificationSummary currentCertification,
    Instant createdAt,
    Instant updatedAt,
    Long version) {

  /** Converte para o formato de escrita, usado como base do PATCH. */
  public PropertyWrite toWrite() {
    return new PropertyWrite(
        id,
        farmerId,
        name,
        totalArea,
        productionDescription,
        location,
        address,
        accessDirections,
        version);
  }
}
