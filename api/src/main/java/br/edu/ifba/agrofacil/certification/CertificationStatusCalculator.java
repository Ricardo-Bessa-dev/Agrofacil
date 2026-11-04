// Calcula o status efetivo e os dias até o vencimento de uma certificação (doc 1 §2.3.7).
package br.edu.ifba.agrofacil.certification;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/** Função pura: "hoje" é a data no fuso da associação (America/Bahia). */
public final class CertificationStatusCalculator {

  private CertificationStatusCalculator() {}

  /**
   * @param status status efetivo
   * @param daysToExpire negativo se já venceu
   */
  public record Result(CertificationStatus status, long daysToExpire) {}

  /**
   * @param alertDays dias de aviso da associação; o maior define a faixa {@code EXPIRING}
   */
  public static Result calculate(
      ManualStatus manual, LocalDate expiresOn, LocalDate today, Collection<Integer> alertDays) {
    long days = ChronoUnit.DAYS.between(today, expiresOn);
    int maxAlert = alertDays.stream().mapToInt(Integer::intValue).max().orElse(0);
    CertificationStatus status;
    if (manual == ManualStatus.CANCELLED) {
      status = CertificationStatus.CANCELLED;
    } else if (manual == ManualStatus.SUSPENDED) {
      status = CertificationStatus.SUSPENDED;
    } else if (today.isAfter(expiresOn)) {
      status = CertificationStatus.EXPIRED;
    } else if (days <= maxAlert) {
      status = CertificationStatus.EXPIRING;
    } else {
      status = CertificationStatus.VALID;
    }
    return new Result(status, days);
  }

  /** {@code VALID} e {@code EXPIRING} contam como "certificação regular" (RN07). */
  public static boolean isInForce(CertificationStatus status) {
    return status == CertificationStatus.VALID || status == CertificationStatus.EXPIRING;
  }
}
