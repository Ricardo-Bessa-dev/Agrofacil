// Situação das visitas de uma propriedade no ciclo atual, no formato do contrato (doc 1 §3.15,
// RN29).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.common.web.IdName;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param group grupo do SPG do agricultor, se houver
 * @param daysToExpire nulo sem certificação
 * @param warnings avisos prontos para exibir, ex.: "Sem localização: …"
 */
public record PropertyVisitStatus(
    PropertyRef property,
    IdName group,
    Cycle cycle,
    LocalDate windowOpensOn,
    boolean windowOpen,
    Long daysToExpire,
    Count peer,
    Count verification,
    VisitCycleCalculator.State state,
    List<String> warnings) {

  public record PropertyRef(UUID id, String name, UUID farmerId, String farmerName) {}

  public record Cycle(LocalDate from, LocalDate to, UUID certificationId) {}

  /**
   * @param required exigidas no ciclo (verificação: 1)
   * @param done concluídas no ciclo
   * @param planned marcadas e ainda não feitas
   */
  public record Count(long required, long done, long planned) {}
}
