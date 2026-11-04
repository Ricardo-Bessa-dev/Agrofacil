// Token de uso único enviado por e-mail: convite de coordenador ou redefinição de senha.
package br.edu.ifba.agrofacil.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_token")
public class UserToken {

  public enum Purpose {
    PASSWORD_RESET,
    INVITE
  }

  @Id private UUID id;
  private UUID userId;

  @Enumerated(EnumType.STRING)
  private Purpose purpose;

  @JdbcTypeCode(SqlTypes.CHAR) // coluna char(n)
  private String tokenHash; // SHA-256

  private Instant expiresAt;
  private Instant usedAt;
  private Instant createdAt;

  protected UserToken() {}

  public UserToken(UUID userId, Purpose purpose, String tokenHash, Instant expiresAt, Instant now) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.purpose = purpose;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.createdAt = now;
  }

  public boolean isUsable(Instant now) {
    return usedAt == null && expiresAt.isAfter(now);
  }

  public void markUsed(Instant when) {
    usedAt = when;
  }

  public UUID getUserId() {
    return userId;
  }

  public Purpose getPurpose() {
    return purpose;
  }
}
