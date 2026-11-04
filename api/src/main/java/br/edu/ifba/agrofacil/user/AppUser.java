// Usuário do sistema: agricultor (CPF, sem senha) ou coordenador (e-mail + senha + 2FA).
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.common.persistence.SyncableEntity;
import br.edu.ifba.agrofacil.common.security.Role;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.type.SqlTypes;

/** O perfil do agricultor fica em {@link FarmerProfile}, com o mesmo id. */
@Entity
@Table(name = "app_user")
@Audited
public class AppUser extends SyncableEntity {

  private UUID associationId;

  @Enumerated(EnumType.STRING)
  private Role role;

  private String name;

  @JdbcTypeCode(SqlTypes.CHAR) // coluna char(n)
  private String cpf; // 11 dígitos, sem máscara (RN02)

  private String email;
  private String phone; // E.164

  @NotAudited private String passwordHash; // Argon2id; só coordenador

  @Enumerated(EnumType.STRING)
  private UserStatus status;

  private Instant termsAcceptedAt;
  private String termsVersion;
  @NotAudited private Instant lastLoginAt;
  @NotAudited private Instant syncResetAt;

  protected AppUser() {}

  private AppUser(UUID associationId, Role role, String name, UserStatus status) {
    super(UUID.randomUUID());
    this.associationId = associationId;
    this.role = role;
    this.name = name;
    this.status = status;
  }

  public static AppUser newFarmer(UUID associationId, String name, String cpf) {
    var user = new AppUser(associationId, Role.FARMER, name, UserStatus.ACTIVE);
    user.cpf = cpf;
    return user;
  }

  /** Coordenador convidado: fica inativo e sem senha até aceitar o convite (ADR-04). */
  public static AppUser newInvitedCoordinator(UUID associationId, String name, String email) {
    var user = new AppUser(associationId, Role.COORDINATOR, name, UserStatus.INACTIVE);
    user.email = email;
    return user;
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  public boolean isFarmer() {
    return role == Role.FARMER;
  }

  public void acceptInvite(String name, String passwordHash) {
    this.name = name;
    this.passwordHash = passwordHash;
    this.status = UserStatus.ACTIVE;
  }

  public void acceptTerms(String version, Instant when) {
    this.termsVersion = version;
    this.termsAcceptedAt = when;
  }

  /** Pede ao app que apague o espelho local e puxe tudo de novo (doc 1 §4.8). */
  public void requestSyncReset(Instant when) {
    this.syncResetAt = when;
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public Role getRole() {
    return role;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCpf() {
    return cpf;
  }

  public void setCpf(String cpf) {
    this.cpf = cpf;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public UserStatus getStatus() {
    return status;
  }

  public void setStatus(UserStatus status) {
    this.status = status;
  }

  public Instant getTermsAcceptedAt() {
    return termsAcceptedAt;
  }

  public String getTermsVersion() {
    return termsVersion;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public void setLastLoginAt(Instant lastLoginAt) {
    this.lastLoginAt = lastLoginAt;
  }

  public Instant getSyncResetAt() {
    return syncResetAt;
  }
}
