// Entidade versionada com exclusão lógica (ADR-10): a linha fica e vira lápide no pull.
package br.edu.ifba.agrofacil.common.persistence;

import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;

/**
 * Toda consulta de tela filtra {@code deleted_at is null}; o pull usa as excluídas como lápides.
 */
@MappedSuperclass
@Audited
public abstract class SyncableEntity extends BaseEntity {

  private Instant deletedAt;

  protected SyncableEntity() {}

  protected SyncableEntity(UUID id) {
    super(id);
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  /** Marca como excluída, sem apagar a linha. */
  public void markDeleted(Instant when) {
    this.deletedAt = when;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }
}
