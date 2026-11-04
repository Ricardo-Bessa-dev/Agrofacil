// Na primeira subida, convida o primeiro coordenador pelo e-mail configurado (doc 2 §1.7.5).
package br.edu.ifba.agrofacil.auth;

import br.edu.ifba.agrofacil.common.security.Role;
import br.edu.ifba.agrofacil.config.AppProperties;
import br.edu.ifba.agrofacil.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Só age se não houver nenhum coordenador e {@code BOOTSTRAP_COORDINATOR_EMAIL} estiver definido.
 */
@Component
public class BootstrapCoordinatorRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapCoordinatorRunner.class);
  private static final String DEFAULT_NAME = "Coordenador";

  private final UserRepository users;
  private final AccountService accounts;
  private final AppProperties props;

  public BootstrapCoordinatorRunner(
      UserRepository users, AccountService accounts, AppProperties props) {
    this.users = users;
    this.accounts = accounts;
    this.props = props;
  }

  @Override
  public void run(ApplicationArguments args) {
    String email = props.bootstrap().coordinatorEmail();
    if (email == null
        || email.isBlank()
        || users.countByRoleAndDeletedAtIsNull(Role.COORDINATOR) > 0) {
      return;
    }
    accounts.invite(props.bootstrap().associationId(), DEFAULT_NAME, email);
    log.info(
        "Primeiro coordenador convidado. Confira a caixa de entrada (Mailpit no desenvolvimento).");
  }
}
