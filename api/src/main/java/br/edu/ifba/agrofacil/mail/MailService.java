// Envia e-mails só depois do commit, para nunca mandar algo que deu rollback (doc 2 §1.12.3).
package br.edu.ifba.agrofacil.mail;

import br.edu.ifba.agrofacil.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Falha no SMTP vai só para o log: a operação que pediu o e-mail já foi gravada. */
@Service
public class MailService {

  private static final Logger log = LoggerFactory.getLogger(MailService.class);

  private final ApplicationEventPublisher events;
  private final MailGateway gateway;
  private final AppProperties props;

  public MailService(ApplicationEventPublisher events, MailGateway gateway, AppProperties props) {
    this.events = events;
    this.gateway = gateway;
    this.props = props;
  }

  /** Agenda o envio para depois do commit (ou na hora, se não houver transação). */
  public void sendAfterCommit(MailMessage message) {
    events.publishEvent(message);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  void deliver(MailMessage message) {
    if (props.logMailLinks()) {
      // Só no desenvolvimento: facilita testar convite e redefinição sem abrir o Mailpit.
      log.info("E-mail \"{}\":\n{}", message.subject(), message.body());
    }
    try {
      gateway.send(message);
    } catch (RuntimeException e) {
      log.warn("Falha ao enviar e-mail \"{}\": {}", message.subject(), e.getMessage());
    }
  }
}
