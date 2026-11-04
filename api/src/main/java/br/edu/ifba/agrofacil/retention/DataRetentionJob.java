// Rotina mensal de retenção (LGPD): apaga códigos, tokens e mutações de sync vencidos (doc 1 §5.7).
package br.edu.ifba.agrofacil.retention;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Só apaga o que não é prova: credenciais de uso único, sessões encerradas e o registro de
 * idempotência do push. A anonimização dos agricultores desligados depende da data de desligamento
 * e de um relatório de simulação conferido pelo coordenador, e ainda não está implementada.
 */
@Component
public class DataRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(DataRetentionJob.class);

  /** Códigos, desafios 2FA e tokens: 30 dias depois de usados, revogados ou vencidos. */
  static final Duration CREDENTIALS_KEEP = Duration.ofDays(30);

  /** Registro de idempotência do push: o app reenvia um lote em minutos, não em meses. */
  static final Duration SYNC_MUTATIONS_KEEP = Duration.ofDays(90);

  private final JdbcClient jdbc;
  private final Clock clock;

  public DataRetentionJob(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /**
   * Roda no dia 1 de cada mês, de madrugada.
   *
   * @return linhas apagadas por tabela (também vai para o log)
   */
  @Scheduled(cron = "0 30 3 1 * *", zone = "${app.timezone}")
  @Transactional
  public Map<String, Integer> run() {
    var limit = Timestamp.from(clock.instant().minus(CREDENTIALS_KEEP));
    var result = new LinkedHashMap<String, Integer>();
    result.put(
        "access_code",
        jdbc.sql(
                "delete from access_code where coalesce(used_at, revoked_at, expires_at) < ?"
                    + " and (used_at is not null or revoked_at is not null or expires_at < ?)")
            .params(limit, limit)
            .update());
    result.put(
        "login_challenge",
        jdbc.sql("delete from login_challenge where coalesce(used_at, expires_at) < ?")
            .param(limit)
            .update());
    result.put(
        "user_token",
        jdbc.sql("delete from user_token where coalesce(used_at, expires_at) < ?")
            .param(limit)
            .update());
    // Um refresh usado continua na família (detecção de reutilização) até a família acabar.
    result.put(
        "refresh_token",
        jdbc.sql(
                "delete from refresh_token where (revoked_at is not null and revoked_at < ?)"
                    + " or expires_at < ?")
            .params(limit, limit)
            .update());
    result.put(
        "sync_mutation",
        jdbc.sql("delete from sync_mutation where processed_at < ?")
            .param(Timestamp.from(clock.instant().minus(SYNC_MUTATIONS_KEEP)))
            .update());
    log.info("Retenção de dados: {}", result);
    return result;
  }
}
