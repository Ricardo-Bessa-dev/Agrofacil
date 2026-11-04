// Lê o usuário atual do contexto do Spring Security.
package br.edu.ifba.agrofacil.common.security;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Os controllers pegam o usuário aqui e o passam aos serviços como parâmetro. */
@Component
public class CurrentUserProvider {

  public static final String CLAIM_ROLE = "role";
  public static final String CLAIM_ASSOCIATION = "assoc";
  public static final String CLAIM_DEVICE = "dev";

  /** Usuário autenticado; 401 se não houver. */
  public CurrentUser get() {
    return currentOrNull().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
  }

  /** Usuário autenticado, se houver (usado pela auditoria e por rotas públicas). */
  public static Optional<CurrentUser> currentOrNull() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
      return Optional.empty();
    }
    String device = jwt.getClaimAsString(CLAIM_DEVICE);
    return Optional.of(
        new CurrentUser(
            UUID.fromString(jwt.getSubject()),
            Role.valueOf(jwt.getClaimAsString(CLAIM_ROLE)),
            UUID.fromString(jwt.getClaimAsString(CLAIM_ASSOCIATION)),
            device != null ? UUID.fromString(device) : null));
  }
}
