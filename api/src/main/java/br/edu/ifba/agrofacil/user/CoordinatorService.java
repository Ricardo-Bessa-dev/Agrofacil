// Gestão de coordenadores: lista, convite e ativação/desativação (doc 1 §3.7.2, RN24).
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.auth.AccountService;
import br.edu.ifba.agrofacil.auth.TokenService;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.security.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoordinatorService {

  private final UserRepository users;
  private final AccountService accounts;
  private final TokenService tokens;

  public CoordinatorService(UserRepository users, AccountService accounts, TokenService tokens) {
    this.users = users;
    this.accounts = accounts;
    this.tokens = tokens;
  }

  @Transactional(readOnly = true)
  public List<CoordinatorView> list(CurrentUser user) {
    return users
        .findByAssociationIdAndRoleAndDeletedAtIsNullOrderByName(
            user.associationId(), Role.COORDINATOR)
        .stream()
        .map(CoordinatorView::of)
        .toList();
  }

  /** Convida um coordenador da mesma associação (ADR-04). */
  @Transactional
  public void invite(CurrentUser user, String name, String email) {
    accounts.invite(user.associationId(), name, email);
  }

  /**
   * Ativa ou desativa. Desativar derruba as sessões.
   *
   * @throws ApiException 409 LAST_COORDINATOR ao desativar o último ativo (RN24)
   */
  @Transactional
  public CoordinatorView changeStatus(CurrentUser user, UUID id, UserStatus status) {
    var coordinator =
        users
            .findByIdAndAssociationIdAndRoleAndDeletedAtIsNull(
                id, user.associationId(), Role.COORDINATOR)
            .orElseThrow(ApiException::notFound);
    if (status == UserStatus.INACTIVE && coordinator.isActive()) {
      long active =
          users.countByAssociationIdAndRoleAndStatusAndDeletedAtIsNull(
              user.associationId(), Role.COORDINATOR, UserStatus.ACTIVE);
      if (active <= 1) {
        throw new ApiException(ErrorCode.LAST_COORDINATOR);
      }
      tokens.revokeAllForUser(id);
    }
    if (status == UserStatus.ACTIVE && coordinator.getPasswordHash() == null) {
      // Quem não aceitou o convite não tem senha; ativar não adiantaria.
      throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
    }
    coordinator.setStatus(status);
    return CoordinatorView.of(coordinator);
  }
}
