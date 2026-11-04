// Regras fixas de quem pode participar de uma visita (RN32); a sugestão e a gravação usam esta
// classe.
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.spg.visit.VisitorModel.Candidate;
import br.edu.ifba.agrofacil.spg.visit.VisitorModel.Target;
import java.util.Optional;

/** Função pura. O motivo sai pronto em português; o do impedimento é sempre genérico (RN40). */
public final class VisitorEligibility {

  private VisitorEligibility() {}

  /**
   * @param sameGroupAllowed {@code association.verification_same_group_allowed}
   * @return vazio se pode participar; senão, o motivo
   */
  public static Optional<String> check(
      Candidate c,
      Target target,
      VisitType type,
      VisitParticipant.Role role,
      boolean sameGroupAllowed) {
    if (c.person().isFarmer() && c.person().id().equals(target.farmerId())) {
      return Optional.of("é o agricultor visitado");
    }
    if (!c.active()) {
      return Optional.of("está inativo");
    }
    if (c.conflictWithTarget()) {
      return Optional.of("impedimento cadastrado com " + target.farmerName());
    }
    if (role == VisitParticipant.Role.OBSERVER) {
      return Optional.empty();
    }
    if (type == VisitType.PEER) {
      if (!c.person().isFarmer()) {
        return Optional.of("só agricultores do grupo fazem a visita de pares");
      }
      if (target.groupId() == null || !target.groupId().equals(c.groupId())) {
        return Optional.of("não é do mesmo grupo do visitado");
      }
      return Optional.empty();
    }
    if (!c.isCommitteeMember()) {
      return Optional.of("não faz parte da Comissão de Avaliação");
    }
    if (!sameGroupAllowed && c.committeeGroupId().equals(target.groupId())) {
      return Optional.of("representa o mesmo grupo do visitado na Comissão");
    }
    return Optional.empty();
  }
}
