// Pedido de correção do coordenador para um registro; não há aprovação de registros (ADR-06, RN17).
package br.edu.ifba.agrofacil.correction;

import br.edu.ifba.agrofacil.common.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "correction_request")
public class CorrectionRequest extends BaseEntity {

  public enum Status {
    OPEN,
    RESOLVED
  }

  private UUID recordId;
  private String message;

  @Enumerated(EnumType.STRING)
  private Status status;

  private UUID createdBy;
  private UUID resolvedBy;
  private Instant resolvedAt;

  protected CorrectionRequest() {}

  public CorrectionRequest(UUID recordId, String message, UUID createdBy) {
    super(UUID.randomUUID());
    this.recordId = recordId;
    this.message = message;
    this.createdBy = createdBy;
    this.status = Status.OPEN;
  }

  public boolean isOpen() {
    return status == Status.OPEN;
  }

  public void resolve(UUID by, Instant when) {
    if (isOpen()) {
      status = Status.RESOLVED;
      resolvedBy = by;
      resolvedAt = when;
    }
  }

  public UUID getRecordId() {
    return recordId;
  }

  public String getMessage() {
    return message;
  }

  public Status getStatus() {
    return status;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public UUID getResolvedBy() {
    return resolvedBy;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }
}
