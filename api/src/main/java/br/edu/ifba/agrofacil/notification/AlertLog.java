// Registro dos avisos de rotina já enviados (tabela alert_log), para cada aviso sair uma vez só
// (RN30).
package br.edu.ifba.agrofacil.notification;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AlertLog {

  private final JdbcClient jdbc;
  private final Clock clock;

  public AlertLog(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /**
   * Marca o aviso como enviado.
   *
   * @param key ex.: {@code VISIT_REMINDER:<visitId>:<data>}
   * @return true se ainda não tinha sido enviado (quem chamou deve enviar agora)
   */
  public boolean markIfNew(UUID subjectId, String key) {
    return jdbc.sql(
                "insert into alert_log (subject_id, alert_key, sent_at) values (?, ?, ?)"
                    + " on conflict do nothing")
            .params(subjectId, key, Timestamp.from(clock.instant()))
            .update()
        == 1;
  }
}
