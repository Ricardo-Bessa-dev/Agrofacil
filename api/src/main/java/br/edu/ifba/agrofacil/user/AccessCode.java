// Código de 6 dígitos que ativa um aparelho do agricultor (ADR-03, RN03). Só o hash é guardado.
package br.edu.ifba.agrofacil.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "access_code")
public class AccessCode {

  /** O código fica bloqueado depois de 5 tentativas erradas (RN03). */
  public static final int MAX_ATTEMPTS = 5;

  @Id private UUID id;
  private UUID farmerId;
  private String codeHash; // Argon2id
  private Instant expiresAt;
  private int failedAttempts;
  private Instant usedAt;
  private Instant revokedAt;
  private UUID createdBy;
  private Instant createdAt;

  protected AccessCode() {}

  public AccessCode(
      UUID farmerId, String codeHash, Instant expiresAt, UUID createdBy, Instant now) {
    this.id = UUID.randomUUID();
    this.farmerId = farmerId;
    this.codeHash = codeHash;
    this.expiresAt = expiresAt;
    this.createdBy = createdBy;
    this.createdAt = now;
  }

  public boolean isLocked() {
    return failedAttempts >= MAX_ATTEMPTS;
  }

  public boolean isPending(Instant now) {
    return usedAt == null && revokedAt == null && expiresAt.isAfter(now) && !isLocked();
  }

  public void registerFailedAttempt() {
    failedAttempts++;
  }

  public void markUsed(Instant when) {
    usedAt = when;
  }

  public void revoke(Instant when) {
    if (usedAt == null && revokedAt == null) {
      revokedAt = when;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getFarmerId() {
    return farmerId;
  }

  public String getCodeHash() {
    return codeHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public int getFailedAttempts() {
    return failedAttempts;
  }
}
