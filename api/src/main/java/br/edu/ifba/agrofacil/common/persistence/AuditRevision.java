// Revisão do Envers enriquecida com o usuário, o aparelho e a origem da alteração (doc 2 §1.6.3).
package br.edu.ifba.agrofacil.common.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

@Entity
@Table(name = "audit_revision")
@RevisionEntity(AuditRevisionListener.class)
public class AuditRevision {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @RevisionNumber
  private Long id;

  /** Momento da revisão, em milissegundos desde 1970 (UTC). */
  @RevisionTimestamp private long revisionAt;

  private UUID userId;
  private UUID deviceId;

  /** {@code MOBILE}, {@code WEB} ou {@code SYSTEM} (rotinas). */
  private String source;

  public Long getId() {
    return id;
  }

  public long getRevisionAt() {
    return revisionAt;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getDeviceId() {
    return deviceId;
  }

  public String getSource() {
    return source;
  }

  void fill(UUID userId, UUID deviceId, String source) {
    this.userId = userId;
    this.deviceId = deviceId;
    this.source = source;
  }
}
