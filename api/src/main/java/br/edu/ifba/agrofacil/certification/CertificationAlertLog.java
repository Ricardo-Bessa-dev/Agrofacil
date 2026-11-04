// Guarda quais alertas de vencimento já saíram, para cada par (certificação, N) sair uma vez só
// (RN20).
package br.edu.ifba.agrofacil.certification;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Tabela {@code certification_alert}; sem entidade JPA, porque é só um registro de envios. */
@Repository
public class CertificationAlertLog {

  private final JdbcClient jdbc;
  private final Clock clock;

  public CertificationAlertLog(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Limites (dias) já enviados para a certificação. */
  public Set<Integer> sentDays(UUID certificationId) {
    return new HashSet<>(
        jdbc.sql("select days_before from certification_alert where certification_id = ?")
            .param(certificationId)
            .query(Integer.class)
            .list());
  }

  /** Marca os limites como enviados; repetir não duplica. */
  public void markSent(UUID certificationId, Collection<Integer> days) {
    var now = Timestamp.from(clock.instant());
    for (int d : days) {
      jdbc.sql(
              "insert into certification_alert (certification_id, days_before, sent_at)"
                  + " values (?, ?, ?) on conflict do nothing")
          .params(certificationId, d, now)
          .update();
    }
  }
}
