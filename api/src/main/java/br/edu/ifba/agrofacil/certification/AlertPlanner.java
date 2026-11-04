// Decide qual alerta de vencimento enviar hoje para uma certificação (RN20, doc 2 §1.12.1).
package br.edu.ifba.agrofacil.certification;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Função pura, sem Spring e sem banco. */
public final class AlertPlanner {

  /** Marcador do alerta "venceu", gravado em {@code certification_alert.days_before}. */
  public static final int EXPIRED = 0;

  private AlertPlanner() {}

  /**
   * @param sendDays limite que dispara o alerta ({@link #EXPIRED} = "venceu")
   * @param markAsSent limites a gravar como enviados, para não repetir nem mandar vários de uma vez
   */
  public record Plan(int sendDays, Set<Integer> markAsSent) {}

  /**
   * Escolhe o alerta do dia. Se a rotina ficou dias sem rodar, envia só o menor limite que já
   * passou e marca os maiores como enviados.
   *
   * @param thresholds dias de aviso da associação, ex.: [60, 30, 15]
   * @param alreadySent limites já gravados para esta certificação
   * @return vazio se não há alerta a enviar hoje
   */
  public static Optional<Plan> nextAlert(
      LocalDate expiresOn,
      LocalDate today,
      Collection<Integer> thresholds,
      Set<Integer> alreadySent) {
    long daysToExpire = ChronoUnit.DAYS.between(today, expiresOn);
    if (daysToExpire < 0) {
      if (alreadySent.contains(EXPIRED)) {
        return Optional.empty();
      }
      var mark = new TreeSet<>(thresholds);
      mark.add(EXPIRED);
      return Optional.of(new Plan(EXPIRED, mark));
    }
    return thresholds.stream()
        .filter(n -> n > EXPIRED && n >= daysToExpire && !alreadySent.contains(n))
        .min(Integer::compare)
        .map(chosen -> {
          var mark = new TreeSet<Integer>();
          thresholds.stream().filter(n -> n >= chosen).forEach(mark::add);
          return new Plan(chosen, mark);
        });
  }
}
