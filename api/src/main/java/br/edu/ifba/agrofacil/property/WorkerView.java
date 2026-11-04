// Pessoa vinculada no formato do contrato (doc 1 §3.8).
package br.edu.ifba.agrofacil.property;

import java.util.UUID;

public record WorkerView(
    UUID id,
    UUID propertyId,
    String name,
    WorkerRelationship relationship,
    boolean active,
    Long version) {

  public static WorkerView of(Worker w) {
    return new WorkerView(
        w.getId(),
        w.getPropertyId(),
        w.getName(),
        w.getRelationship(),
        w.isActive(),
        w.getVersion());
  }

  public WorkerWrite toWrite() {
    return new WorkerWrite(name, relationship, active, version);
  }
}
