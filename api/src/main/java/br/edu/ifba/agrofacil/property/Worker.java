// Pessoa vinculada à propriedade (família ou empregados), listada no certificado.
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.common.persistence.SyncableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.envers.Audited;

/** O CPF do trabalhador não é coletado (LGPD, necessidade). */
@Entity
@Table(name = "worker")
@Audited
public class Worker extends SyncableEntity {

  private UUID propertyId;
  private String name;

  @Enumerated(EnumType.STRING)
  private WorkerRelationship relationship;

  private boolean active;

  protected Worker() {}

  public Worker(UUID propertyId) {
    super(UUID.randomUUID());
    this.propertyId = propertyId;
  }

  public void update(String name, WorkerRelationship relationship, boolean active) {
    this.name = name;
    this.relationship = relationship;
    this.active = active;
  }

  public UUID getPropertyId() {
    return propertyId;
  }

  public String getName() {
    return name;
  }

  public WorkerRelationship getRelationship() {
    return relationship;
  }

  public boolean isActive() {
    return active;
  }
}
