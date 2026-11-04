// Celular vinculado a um agricultor; o coordenador pode revogá-lo (RN04, RN25).
package br.edu.ifba.agrofacil.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** O id é gerado pelo app na instalação e guardado no SecureStore. */
@Entity
@Table(name = "device")
public class Device {

  @Id private UUID id;
  private UUID userId;
  private String name; // ex.: "Samsung Galaxy A15"
  private String platform; // ANDROID ou IOS
  private String appVersion;
  private String pushToken; // ExponentPushToken[...]
  private Instant createdAt;
  private Instant lastSeenAt;
  private Instant revokedAt;
  private UUID revokedBy;

  protected Device() {}

  public Device(UUID id, UUID userId, Instant now) {
    this.id = id;
    this.userId = userId;
    this.createdAt = now;
    this.lastSeenAt = now;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  /** Vincula (ou revincula) o aparelho a um agricultor e o deixa ativo. */
  public void activateFor(
      UUID userId, String name, String platform, String appVersion, Instant now) {
    this.userId = userId;
    this.name = name;
    this.platform = platform;
    this.appVersion = appVersion;
    this.revokedAt = null;
    this.revokedBy = null;
    this.lastSeenAt = now;
  }

  public void revoke(UUID by, Instant when) {
    if (revokedAt == null) {
      this.revokedAt = when;
      this.revokedBy = by;
      this.pushToken = null;
    }
  }

  public void touch(Instant now) {
    this.lastSeenAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPlatform() {
    return platform;
  }

  public String getAppVersion() {
    return appVersion;
  }

  public void setAppVersion(String appVersion) {
    this.appVersion = appVersion;
  }

  public String getPushToken() {
    return pushToken;
  }

  public void setPushToken(String pushToken) {
    this.pushToken = pushToken;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }
}
