// Testes das funções puras do SPG: ciclo (RN29), quem pode visitar (RN32) e pontuação (RN33).
package br.edu.ifba.agrofacil.spg;

import static org.assertj.core.api.Assertions.assertThat;

import br.edu.ifba.agrofacil.spg.people.PersonRef;
import br.edu.ifba.agrofacil.spg.visit.GeoMath;
import br.edu.ifba.agrofacil.spg.visit.VisitCycleCalculator;
import br.edu.ifba.agrofacil.spg.visit.VisitCycleCalculator.CertificationDates;
import br.edu.ifba.agrofacil.spg.visit.VisitCycleCalculator.State;
import br.edu.ifba.agrofacil.spg.visit.VisitCycleCalculator.VisitDone;
import br.edu.ifba.agrofacil.spg.visit.VisitCycleCalculator.VisitSettings;
import br.edu.ifba.agrofacil.spg.visit.VisitParticipant;
import br.edu.ifba.agrofacil.spg.visit.VisitType;
import br.edu.ifba.agrofacil.spg.visit.VisitorEligibility;
import br.edu.ifba.agrofacil.spg.visit.VisitorModel.Candidate;
import br.edu.ifba.agrofacil.spg.visit.VisitorModel.Place;
import br.edu.ifba.agrofacil.spg.visit.VisitorModel.Target;
import br.edu.ifba.agrofacil.spg.visit.VisitorScoring;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisitRulesTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
  private static final VisitSettings SETTINGS = new VisitSettings(90, 2);
  private static final UUID GROUP = UUID.randomUUID();
  private static final UUID OTHER_GROUP = UUID.randomUUID();
  private static final Place IRECE = new Place(-11.3045, -41.8553, 2914604);
  private static final Target TARGET =
      new Target(UUID.randomUUID(), UUID.randomUUID(), "José", GROUP, IRECE);

  private static CertificationDates expiringIn(int days) {
    var expires = TODAY.plusDays(days);
    return new CertificationDates(expires.minusYears(1), expires);
  }

  @Test
  void withoutCertification_cycleIsTheLast12Months_andWindowIsClosed_RN29() {
    var r = VisitCycleCalculator.calculate(null, List.of(), SETTINGS, TODAY);
    assertThat(r.from()).isEqualTo(TODAY.minusMonths(12));
    assertThat(r.windowOpen()).isFalse();
    assertThat(r.state()).isEqualTo(State.PENDING);
  }

  @Test
  void urgent_withThirtyDaysLeft_butNotWithThirtyOne_RN29() {
    assertThat(VisitCycleCalculator.calculate(expiringIn(30), List.of(), SETTINGS, TODAY).state())
        .isEqualTo(State.URGENT);
    assertThat(VisitCycleCalculator.calculate(expiringIn(31), List.of(), SETTINGS, TODAY).state())
        .isEqualTo(State.PENDING);
  }

  @Test
  void upToDate_onlyCountsVisitsInsideTheCycle_RN29() {
    var cert = expiringIn(60);
    var inside =
        List.of(
            new VisitDone(VisitType.PEER, cert.issuedOn().plusDays(10)),
            new VisitDone(VisitType.PEER, cert.issuedOn().plusDays(100)),
            new VisitDone(VisitType.VERIFICATION, TODAY.minusDays(5)));
    assertThat(VisitCycleCalculator.calculate(cert, inside, SETTINGS, TODAY).state())
        .isEqualTo(State.UP_TO_DATE);
    var oneOutside =
        List.of(
            new VisitDone(VisitType.PEER, cert.issuedOn().minusDays(1)),
            new VisitDone(VisitType.PEER, cert.issuedOn().plusDays(100)),
            new VisitDone(VisitType.VERIFICATION, TODAY.minusDays(5)));
    var r = VisitCycleCalculator.calculate(cert, oneOutside, SETTINGS, TODAY);
    assertThat(r.peerDone()).isEqualTo(1);
    assertThat(r.state()).isEqualTo(State.PENDING);
    assertThat(r.windowOpen()).isTrue(); // 60 dias para vencer, período de 90
  }

  private static Candidate farmer(UUID group, UUID committeeGroup, boolean conflict) {
    return new Candidate(
        PersonRef.farmer(UUID.randomUUID(), "Ana"),
        true,
        group,
        committeeGroup,
        List.of(IRECE),
        conflict,
        false,
        false,
        false,
        0);
  }

  @Test
  void peerVisit_requiresSameGroup_andRespectsConflicts_RN32() {
    var peer = VisitParticipant.Role.VISITOR;
    assertThat(
            VisitorEligibility.check(
                farmer(GROUP, null, false), TARGET, VisitType.PEER, peer, false))
        .isEmpty();
    assertThat(
            VisitorEligibility.check(
                farmer(OTHER_GROUP, null, false), TARGET, VisitType.PEER, peer, false))
        .contains("não é do mesmo grupo do visitado");
    assertThat(
            VisitorEligibility.check(
                farmer(GROUP, null, true), TARGET, VisitType.PEER, peer, false))
        .contains("impedimento cadastrado com José"); // motivo genérico (RN40)
  }

  @Test
  void verification_requiresCommittee_andOtherGroupUnlessAllowed_RN32() {
    var visitor = VisitParticipant.Role.VISITOR;
    assertThat(
            VisitorEligibility.check(
                farmer(GROUP, null, false), TARGET, VisitType.VERIFICATION, visitor, false))
        .contains("não faz parte da Comissão de Avaliação");
    var sameGroupMember = farmer(GROUP, GROUP, false);
    assertThat(
            VisitorEligibility.check(
                sameGroupMember, TARGET, VisitType.VERIFICATION, visitor, false))
        .isPresent();
    assertThat(
            VisitorEligibility.check(
                sameGroupMember, TARGET, VisitType.VERIFICATION, visitor, true))
        .isEmpty();
  }

  @Test
  void scoring_withGps_rotation_reciprocity_andBalance_RN33() {
    var near = new Place(-11.30, -41.86, 2914604);
    var base =
        new Candidate(
            PersonRef.farmer(UUID.randomUUID(), "Ana"),
            true,
            GROUP,
            null,
            List.of(near),
            false,
            false,
            false,
            false,
            0);
    var s = VisitorScoring.score(base, TARGET);
    assertThat(s.distanceEstimated()).isFalse();
    assertThat(s.reasons()).first().asString().startsWith("a 0,");

    var penalized =
        new Candidate(base.person(), true, GROUP, null, List.of(near), false, false, true, true, 2);
    var p = VisitorScoring.score(penalized, TARGET);
    assertThat(p.score() - s.score())
        .isEqualTo(30 + 30 + 20, org.assertj.core.data.Offset.offset(0.001));
    assertThat(p.reasons())
        .contains(
            "já visitou esta roça no último ano",
            "foi visitado por este agricultor no último ano",
            "2 visita(s) marcada(s)");
  }

  @Test
  void scoring_withoutGps_estimatesByMunicipality_RN33() {
    var noGps =
        new Candidate(
            PersonRef.farmer(UUID.randomUUID(), "Ana"),
            true,
            GROUP,
            null,
            List.of(new Place(null, null, 2914604)),
            false,
            false,
            false,
            false,
            0);
    var s = VisitorScoring.score(noGps, TARGET);
    assertThat(s.distanceEstimated()).isTrue();
    assertThat(s.distanceKm()).isEqualTo(15);
    assertThat(s.reasons().getFirst()).isEqualTo("a ~15 km (sem localização)");
  }

  @Test
  void memberConflict_ordersThePairLikePostgres_notLikeJavaCompareTo() {
    var low = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var high = UUID.fromString("80000000-0000-0000-0000-000000000001"); // negativo para o Java
    var c =
        new br.edu.ifba.agrofacil.spg.people.MemberConflict(
            UUID.randomUUID(),
            high,
            low,
            br.edu.ifba.agrofacil.spg.people.MemberConflict.Reason.FAMILY,
            null);
    assertThat(c.getFarmerAId()).isEqualTo(low);
    assertThat(c.otherThan(low)).isEqualTo(high);
  }

  @Test
  void haversine_ireceToSalvador_isAboutFourHundredKm() {
    assertThat(GeoMath.haversineKm(-11.3045, -41.8553, -12.9714, -38.5014)).isBetween(390.0, 420.0);
  }
}
