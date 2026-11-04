// Calcula o ciclo, o período de visitas e a situação de uma propriedade (RN29).
package br.edu.ifba.agrofacil.spg.visit;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/** Função pura, testada sem Spring e sem banco (doc 2 §1.15.1). */
public final class VisitCycleCalculator {

  /** Faltando esse tempo ou menos para vencer, sem a verificação, a situação é URGENT. */
  static final int URGENT_DAYS = 30;

  /** Sem certificação, o ciclo são os 12 meses até hoje. */
  private static final int CYCLE_MONTHS_WITHOUT_CERTIFICATION = 12;

  private VisitCycleCalculator() {}

  public enum State {
    UP_TO_DATE,
    PENDING,
    URGENT
  }

  /** Datas da certificação atual que definem o ciclo. */
  public record CertificationDates(LocalDate issuedOn, LocalDate expiresOn) {}

  /** Visita concluída (DONE) da propriedade. */
  public record VisitDone(VisitType type, LocalDate doneOn) {}

  /**
   * @param visitWindowDays o período de visitas abre esse número de dias antes do vencimento
   * @param peerVisitsPerYear visitas de pares exigidas por ciclo
   */
  public record VisitSettings(int visitWindowDays, int peerVisitsPerYear) {}

  /**
   * @param windowOpensOn nulo sem certificação
   * @param daysToExpire nulo sem certificação; negativo se já venceu
   */
  public record Result(
      LocalDate from,
      LocalDate to,
      LocalDate windowOpensOn,
      boolean windowOpen,
      Long daysToExpire,
      long peerDone,
      long verificationDone,
      State state) {}

  /**
   * @param certification certificação atual, ou null se a propriedade não tiver
   * @param doneVisits visitas DONE da propriedade (as fora do ciclo são ignoradas)
   */
  public static Result calculate(
      CertificationDates certification,
      Collection<VisitDone> doneVisits,
      VisitSettings settings,
      LocalDate today) {
    var from =
        certification != null
            ? certification.issuedOn()
            : today.minusMonths(CYCLE_MONTHS_WITHOUT_CERTIFICATION);
    var to = certification != null ? certification.expiresOn() : today;
    var windowOpensOn = certification != null ? to.minusDays(settings.visitWindowDays()) : null;
    boolean windowOpen = windowOpensOn != null && !today.isBefore(windowOpensOn);

    long peerDone = countInCycle(doneVisits, VisitType.PEER, from, to);
    long verificationDone = countInCycle(doneVisits, VisitType.VERIFICATION, from, to);
    boolean complete = peerDone >= settings.peerVisitsPerYear() && verificationDone >= 1;

    Long daysToExpire = certification != null ? ChronoUnit.DAYS.between(today, to) : null;
    boolean urgent =
        windowOpen && verificationDone == 0 && daysToExpire != null && daysToExpire <= URGENT_DAYS;

    var state = complete ? State.UP_TO_DATE : urgent ? State.URGENT : State.PENDING;
    return new Result(
        from, to, windowOpensOn, windowOpen, daysToExpire, peerDone, verificationDone, state);
  }

  private static long countInCycle(
      Collection<VisitDone> visits, VisitType type, LocalDate from, LocalDate to) {
    return visits.stream()
        .filter(v -> v.type() == type)
        .filter(v -> !v.doneOn().isBefore(from) && !v.doneOn().isAfter(to))
        .count();
  }
}
