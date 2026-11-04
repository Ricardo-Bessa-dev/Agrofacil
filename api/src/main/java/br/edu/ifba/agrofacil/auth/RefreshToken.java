// Refresh token opaco e rotativo; só o SHA-256 fica no banco (RN04, doc 2 §1.7.4).
package br.edu.ifba.agrofacil.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tokens da mesma sessão compartilham a {@code familyId}; reutilização revoga a família. */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

  @Id private UUID id;
  private UUID userId;
  private UUID deviceId; // nulo no web
  private UUID familyId;

  @JdbcTypeCode(SqlTypes.CHAR) // coluna char(n)
  private String tokenHash;

  private Instant expiresAt;
  private Instant createdAt;
  private Instant usedAt;
  private Instant revokedAt;

  protected RefreshToken() {}

  public RefreshToken(
      UUID userId, UUID deviceId, UUID familyId, String tokenHash, Instant expiresAt, Instant now) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.deviceId = deviceId;
    this.familyId = familyId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.createdAt = now;
  }

  public void markUsed(Instant when) {
    usedAt = when;
  }

  public void revoke(Instant when) {
    if (revokedAt == null) {
      revokedAt = when;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getDeviceId() {
    return deviceId;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }
}
