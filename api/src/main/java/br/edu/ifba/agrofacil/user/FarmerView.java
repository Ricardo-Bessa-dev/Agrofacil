// Ficha do agricultor no painel (doc 1 §3.7.4).
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.common.domain.Address;
import br.edu.ifba.agrofacil.property.PropertySummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FarmerView(
    UUID id,
    String name,
    String cpf,
    String email,
    String phone,
    UserStatus status,
    UUID groupId,
    Integer groupJoinedYear,
    String cafNumber,
    Address address,
    String notes,
    List<PropertySummary> properties,
    long activeDevices,
    boolean hasPendingAccessCode,
    Instant createdAt,
    Long version) {

  public FarmerWrite toWrite() {
    return new FarmerWrite(
        name, cpf, email, phone, groupId, groupJoinedYear, cafNumber, address, notes, version);
  }
}
