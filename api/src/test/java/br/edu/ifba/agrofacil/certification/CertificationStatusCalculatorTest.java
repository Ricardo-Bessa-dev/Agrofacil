// Testes do status efetivo da certificação (doc 1 §2.3.7).
package br.edu.ifba.agrofacil.certification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CertificationStatusCalculatorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
  private static final List<Integer> ALERT_DAYS = List.of(60, 30, 15);

  private static CertificationStatusCalculator.Result calc(
      ManualStatus manual, LocalDate expiresOn) {
    return CertificationStatusCalculator.calculate(manual, expiresOn, TODAY, ALERT_DAYS);
  }

  @Test
  void farFromExpiring_isValid() {
    assertThat(calc(ManualStatus.ACTIVE, TODAY.plusDays(61)).status())
        .isEqualTo(CertificationStatus.VALID);
  }

  @Test
  void withinTheLargestAlertWindow_isExpiring() {
    var result = calc(ManualStatus.ACTIVE, TODAY.plusDays(60));
    assertThat(result.status()).isEqualTo(CertificationStatus.EXPIRING);
    assertThat(result.daysToExpire()).isEqualTo(60);
  }

  @Test
  void expiresToday_isStillExpiring_andTomorrowIsExpired() {
    assertThat(calc(ManualStatus.ACTIVE, TODAY).status()).isEqualTo(CertificationStatus.EXPIRING);
    var expired = calc(ManualStatus.ACTIVE, TODAY.minusDays(1));
    assertThat(expired.status()).isEqualTo(CertificationStatus.EXPIRED);
    assertThat(expired.daysToExpire()).isEqualTo(-1);
  }

  @Test
  void manualStatus_winsOverDates() {
    assertThat(calc(ManualStatus.SUSPENDED, TODAY.plusDays(300)).status())
        .isEqualTo(CertificationStatus.SUSPENDED);
    assertThat(calc(ManualStatus.CANCELLED, TODAY.minusDays(10)).status())
        .isEqualTo(CertificationStatus.CANCELLED);
  }

  @Test
  void onlyValidAndExpiring_areInForce_RN07() {
    assertThat(CertificationStatusCalculator.isInForce(CertificationStatus.VALID)).isTrue();
    assertThat(CertificationStatusCalculator.isInForce(CertificationStatus.EXPIRING)).isTrue();
    assertThat(CertificationStatusCalculator.isInForce(CertificationStatus.EXPIRED)).isFalse();
    assertThat(CertificationStatusCalculator.isInForce(CertificationStatus.SUSPENDED)).isFalse();
  }
}
