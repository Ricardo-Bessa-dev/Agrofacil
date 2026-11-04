// Formatos da Comissão de Avaliação e dos impedimentos (doc 1 §3.15).
package br.edu.ifba.agrofacil.spg.people;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public final class SpgPeopleDtos {

  private SpgPeopleDtos() {}

  /**
   * @param endedOn preenchido ao encerrar a participação
   */
  public record CommitteeWrite(
      @Valid @NotNull PersonRef person,
      @NotNull UUID representedGroupId,
      @NotNull CommitteeMember.Role role,
      @NotNull LocalDate startedOn,
      LocalDate endedOn,
      Long version) {}

  /**
   * @param active participa hoje da Comissão
   */
  public record CommitteeView(
      UUID id,
      PersonRef person,
      UUID representedGroupId,
      CommitteeMember.Role role,
      LocalDate startedOn,
      LocalDate endedOn,
      boolean active,
      Long version) {

    public CommitteeWrite toWrite() {
      return new CommitteeWrite(person, representedGroupId, role, startedOn, endedOn, version);
    }
  }

  public record ConflictWrite(
      @NotNull UUID farmerAId,
      @NotNull UUID farmerBId,
      @NotNull MemberConflict.Reason reason,
      @Size(max = 2000) String notes) {}

  /** Os nomes vão junto para o painel não precisar de outra consulta. */
  public record ConflictView(
      UUID id,
      UUID farmerAId,
      String farmerAName,
      UUID farmerBId,
      String farmerBName,
      MemberConflict.Reason reason,
      String notes) {}
}
