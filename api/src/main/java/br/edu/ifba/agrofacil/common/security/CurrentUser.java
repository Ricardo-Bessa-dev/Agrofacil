// Dados do usuário autenticado extraídos do JWT (doc 2 §1.7.2).
package br.edu.ifba.agrofacil.common.security;

import java.util.UUID;

/**
 * @param id id do usuário ({@code sub})
 * @param role papel ({@code role})
 * @param associationId associação ({@code assoc}); toda consulta filtra por ela
 * @param deviceId aparelho ({@code dev}); só no app do agricultor
 */
public record CurrentUser(UUID id, Role role, UUID associationId, UUID deviceId) {

  public boolean isFarmer() {
    return role == Role.FARMER;
  }

  public boolean isCoordinator() {
    return role == Role.COORDINATOR;
  }
}
