// Dados de entrada das regras de quem pode visitar e da pontuação da sugestão (RN32, RN33).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.spg.people.PersonRef;
import java.util.List;
import java.util.UUID;

/** Records simples, montados em lote pelo serviço e usados pelas funções puras. */
public final class VisitorModel {

  private VisitorModel() {}

  /** Ponto de uma propriedade; latitude/longitude nulas = sem GPS. */
  public record Place(Double lat, Double lng, Integer municipalityId) {

    public boolean hasLocation() {
      return lat != null && lng != null;
    }
  }

  /**
   * Propriedade visitada.
   *
   * @param farmerName usado no motivo genérico do impedimento (RN40)
   */
  public record Target(
      UUID propertyId, UUID farmerId, String farmerName, UUID groupId, Place place) {}

  /**
   * Pessoa que pode ser escalada.
   *
   * @param groupId grupo do agricultor (nulo para colaborador)
   * @param committeeGroupId grupo que representa na Comissão, se for membro ativo
   * @param places propriedades do agricultor (vazio para colaborador)
   * @param conflictWithTarget há impedimento cadastrado com o visitado
   * @param otherVisitSameDay já tem outra visita marcada no mesmo dia
   * @param visitedTargetInLast12Months rodízio
   * @param visitedByTargetFarmerInLast12Months evita a troca "eu visito você, você me visita"
   */
  public record Candidate(
      PersonRef person,
      boolean active,
      UUID groupId,
      UUID committeeGroupId,
      List<Place> places,
      boolean conflictWithTarget,
      boolean otherVisitSameDay,
      boolean visitedTargetInLast12Months,
      boolean visitedByTargetFarmerInLast12Months,
      int plannedVisitsNext30Days) {

    public boolean isCommitteeMember() {
      return committeeGroupId != null;
    }
  }
}
