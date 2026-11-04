// Desafio do 2FA do coordenador: código de 6 dígitos enviado por e-mail (RN05).
package br.edu.ifba.agrofacil.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_challenge")
public class LoginChallenge {

  /** O desafio aceita 5 tentativas (RN05). */
  public static final int MAX_ATTEMPTS = 5;

  @Id private UUID id;
  private UUID userId;
  private String codeHash; // Argon2id
  private Instant expiresAt;
  private int failedAttempts;
  private Instant usedAt;
  private Instant createdAt;

  protected LoginChallenge() {}

  public LoginChallenge(UUID userId, String codeHash, Instant expiresAt, Instant now) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.codeHash = codeHash;
    this.expiresAt = expiresAt;
    this.createdAt = now;
  }

  public boolean isLocked() {
    return failedAttempts >= MAX_ATTEMPTS;
  }

  public boolean isExpired(Instant now) {
    return !expiresAt.isAfter(now);
  }

  public boolean isUsed() {
    return usedAt != null;
  }

  public void registerFailedAttempt() {
    failedAttempts++;
  }

  public void markUsed(Instant when) {
    usedAt = when;
  }

  /** Troca o código (reenvio) e renova o prazo. */
  public void replaceCode(String newHash, Instant newExpiresAt) {
    codeHash = newHash;
    expiresAt = newExpiresAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getCodeHash() {
    return codeHash;
  }
}
