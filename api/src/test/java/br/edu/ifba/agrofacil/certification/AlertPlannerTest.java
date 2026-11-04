// Testes da escolha do alerta de vencimento do dia (RN20).
package br.edu.ifba.agrofacil.certification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AlertPlannerTest {

  private static final List<Integer> THRESHOLDS = List.of(60, 30, 15);
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

  private static AlertPlanner.Plan plan(int daysToExpire, Set<Integer> sent) {
    return AlertPlanner.nextAlert(TODAY.plusDays(daysToExpire), TODAY, THRESHOLDS, sent)
        .orElse(null);
  }

  @Test
  void exactlyOnThreshold_sendsIt_RN20() {
    var p = plan(30, Set.of(60));
    assertThat(p.sendDays()).isEqualTo(30);
    assertThat(p.markAsSent()).containsExactly(30, 60);
  }

  @Test
  void farFromAnyThreshold_sendsNothing_RN20() {
    assertThat(plan(61, Set.of())).isNull();
  }

  @Test
  void betweenThresholds_alreadySent_sendsNothing_RN20() {
    assertThat(plan(20, Set.of(60, 30))).isNull();
  }

  @Test
  void jobDidNotRunForDays_sendsOnlyTheSmallestPassed_andMarksTheBiggerOnes_RN20() {
    var p = plan(14, Set.of());
    assertThat(p.sendDays()).isEqualTo(15);
    assertThat(p.markAsSent()).containsExactly(15, 30, 60);
  }

  @Test
  void expired_sendsTheExpiredAlertOnce_RN20() {
    var p = plan(-1, Set.of(60, 30, 15));
    assertThat(p.sendDays()).isEqualTo(AlertPlanner.EXPIRED);
    assertThat(plan(-2, Set.of(60, 30, 15, AlertPlanner.EXPIRED))).isNull();
  }

  @Test
  void expiresToday_isNotExpiredYet_RN20() {
    assertThat(plan(0, Set.of(60, 30, 15))).isNull();
    assertThat(plan(0, Set.of(60, 30)).sendDays()).isEqualTo(15);
  }
}
