// Coordenador na lista do painel (doc 1 §3.7.2).
package br.edu.ifba.agrofacil.user;

import java.time.Instant;
import java.util.UUID;

/**
 * @param pendingInvite ainda não aceitou o convite
 */
public record CoordinatorView(
    UUID id,
    String name,
    String email,
    String phone,
    UserStatus status,
    boolean pendingInvite,
    Instant lastLoginAt) {

  public static CoordinatorView of(AppUser u) {
    return new CoordinatorView(
        u.getId(),
        u.getName(),
        u.getEmail(),
        u.getPhone(),
        u.getStatus(),
        u.getPasswordHash() == null,
        u.getLastLoginAt());
  }
}
