// Mutação já processada, com a resposta guardada: garante a idempotência do push (doc 1 §4.7).
package br.edu.ifba.agrofacil.sync;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sync_mutation")
public class SyncMutation {

  @Id private UUID id; // mutationId gerado pelo app
  private UUID userId;
  private UUID deviceId;
  private String entity;
  private UUID entityId;
  private String status;

  @JdbcTypeCode(SqlTypes.JSON)
  private String response;

  private Instant processedAt;

  protected SyncMutation() {}

  public SyncMutation(
      UUID id,
      UUID userId,
      UUID deviceId,
      String entity,
      UUID entityId,
      String status,
      String response,
      Instant processedAt) {
    this.id = id;
    this.userId = userId;
    this.deviceId = deviceId;
    this.entity = entity;
    this.entityId = entityId;
    this.status = status;
    this.response = response;
    this.processedAt = processedAt;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getResponse() {
    return response;
  }
}
