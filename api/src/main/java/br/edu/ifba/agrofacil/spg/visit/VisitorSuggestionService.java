// Sugere visitadores por distância, rodízio e equilíbrio; não grava nada (RN33, doc 1 §6.4).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.certification.CertificationReader;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.property.PropertyService;
import br.edu.ifba.agrofacil.spg.visit.VisitDtos.ExcludedVisitor;
import br.edu.ifba.agrofacil.spg.visit.VisitDtos.SuggestedVisitor;
import br.edu.ifba.agrofacil.spg.visit.VisitDtos.VisitorSuggestion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitorSuggestionService {

  private final VisitorDirectory directory;
  private final VisitStatusService statuses;
  private final PropertyService properties;
  private final AssociationService associations;
  private final CertificationReader clock;

  public VisitorSuggestionService(
      VisitorDirectory directory,
      VisitStatusService statuses,
      PropertyService properties,
      AssociationService associations,
      CertificationReader clock) {
    this.directory = directory;
    this.statuses = statuses;
    this.properties = properties;
    this.associations = associations;
    this.clock = clock;
  }

  /** Elegíveis em ordem (os N primeiros pré-marcados), excluídos com o motivo e colaboradores. */
  @Transactional(readOnly = true)
  public VisitorSuggestion suggest(CurrentUser user, VisitDtos.SuggestRequest req) {
    properties.findVisible(user, req.propertyId());
    var association = associations.get(user.associationId());
    var target = directory.target(req.propertyId());
    var pool =
        req.type() == VisitType.PEER
            ? directory.groupFarmers(target.groupId(), target.farmerId())
            : directory.committeeOn(user.associationId(), req.plannedOn());
    var candidates =
        directory.candidates(
            user.associationId(), target, req.plannedOn(), clock.today(), pool, null);

    var scored = new ArrayList<VisitorScoring.Scored>();
    var excluded = new ArrayList<ExcludedVisitor>();
    for (var c : candidates.values()) {
      var reason =
          VisitorEligibility.check(
              c,
              target,
              req.type(),
              VisitParticipant.Role.VISITOR,
              association.isVerificationSameGroupAllowed());
      if (reason.isEmpty() && c.otherVisitSameDay()) {
        reason = java.util.Optional.of("já tem outra visita marcada no mesmo dia");
      }
      if (reason.isPresent()) {
        excluded.add(new ExcludedVisitor(c.person(), reason.get()));
      } else {
        scored.add(VisitorScoring.score(c, target));
      }
    }
    // Empate: desempata pelo nome, para a mesma entrada gerar sempre a mesma lista.
    scored.sort(
        Comparator.comparingDouble(VisitorScoring.Scored::score)
            .thenComparing(
                s -> s.candidate().person().name(), Comparator.nullsLast(String::compareTo)));
    int preselect = association.getVisitorsPerVisit();
    var eligible = new ArrayList<SuggestedVisitor>();
    for (int i = 0; i < scored.size(); i++) {
      var s = scored.get(i);
      eligible.add(
          new SuggestedVisitor(
              s.candidate().person(),
              i < preselect,
              round(s.distanceKm()),
              s.distanceEstimated(),
              round(s.score()),
              s.reasons()));
    }
    Integer sampling = null;
    if (target.groupId() != null) {
      long groupSize =
          statuses.activeFarmersByGroup(user.associationId()).getOrDefault(target.groupId(), 0L);
      sampling = (int) Math.ceil(Math.sqrt(groupSize));
    }
    return new VisitorSuggestion(
        eligible,
        excluded,
        directory.groupCollaborators(user.associationId(), target.groupId()),
        sampling);
  }

  private static double round(double v) {
    return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
  }
}
