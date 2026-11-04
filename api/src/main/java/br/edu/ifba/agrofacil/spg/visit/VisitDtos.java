// Formatos das visitas do SPG no contrato (doc 1 §3.15).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.attachment.AttachmentView;
import br.edu.ifba.agrofacil.common.domain.GeoPoint;
import br.edu.ifba.agrofacil.spg.people.PersonRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class VisitDtos {

  private VisitDtos() {}

  public enum ChecklistAnswer {
    YES,
    NO,
    NOT_APPLICABLE,
    NOT_CHECKED
  }

  /**
   * @param id opcional na escrita; permite apontar o {@code recorderId} para o participante
   */
  public record ParticipantWrite(
      UUID id,
      @Valid @NotNull PersonRef person,
      @NotNull VisitParticipant.Role role,
      Boolean suggested,
      @Size(max = 10) List<@Size(max = 200) String> suggestionReasons) {}

  /**
   * @param recorderId participante agricultor que registra pelo app (id do participante ou da
   *     pessoa); nulo = registro no papel
   * @param version obrigatório só no PATCH
   */
  public record VisitWrite(
      @NotNull UUID propertyId,
      @NotNull VisitType type,
      boolean unannounced,
      @NotNull LocalDate plannedOn,
      @Valid @NotNull @Size(min = 1, max = 20) List<ParticipantWrite> participants,
      UUID recorderId,
      Long version) {}

  public record Answer(
      @NotBlank @Size(max = 60) String itemCode,
      @NotNull ChecklistAnswer answer,
      @Size(max = 500) String note) {}

  /** Resultado enviado pelo painel (PUT) ou pelo app (mutação {@code visit}). */
  public record ResultWrite(
      @NotNull LocalDate doneOn,
      @NotNull @Size(min = 1, max = 20) List<@NotNull UUID> attendedParticipantIds,
      @Valid @NotNull @Size(max = 100) List<Answer> answers,
      @NotNull Boolean issuesFound,
      @Size(max = 2000) String improvementPoints,
      @Size(max = 2000) String recommendations,
      @Size(max = 2000) String notes,
      @Valid GeoPoint location,
      Long version) {}

  public record Reason(@NotBlank @Size(max = 500) String reason) {}

  public record SuggestRequest(
      @NotNull UUID propertyId, @NotNull VisitType type, @NotNull LocalDate plannedOn) {}

  public record ParticipantView(
      UUID id,
      PersonRef person,
      VisitParticipant.Role role,
      boolean suggested,
      List<String> suggestionReasons,
      Boolean attended) {}

  public record Result(
      LocalDate doneOn,
      List<UUID> attendedParticipantIds,
      List<Answer> answers,
      boolean issuesFound,
      String improvementPoints,
      String recommendations,
      String notes,
      GeoPoint location,
      Visit.ResultSource source,
      UUID recordedById,
      Instant recordedAt) {}

  public record PropertyRef(
      UUID id, String name, UUID farmerId, String farmerName, String municipalityName) {}

  /**
   * @param brief só para o painel e para os participantes; nulo para o visitado (RN36)
   * @param warnings avisos do painel ao marcar ou alterar (ex.: duas visitas no mesmo dia, RN34)
   */
  public record VisitView(
      UUID id,
      UUID propertyId,
      PropertyRef property,
      UUID groupId,
      UUID certificationId,
      VisitType type,
      boolean unannounced,
      VisitStatus status,
      LocalDate plannedOn,
      List<ParticipantView> participants,
      UUID recorderId,
      Result result,
      String cancelReason,
      VisitBrief brief,
      List<AttachmentView> attachments,
      List<String> warnings,
      Instant createdAt,
      Instant updatedAt,
      Long version) {}

  public record SuggestedVisitor(
      PersonRef person,
      boolean preselected,
      Double distanceKm,
      boolean distanceEstimated,
      double score,
      List<String> reasons) {}

  public record ExcludedVisitor(PersonRef person, String reason) {}

  /**
   * @param samplingMinimum ⌈√n⌉ dos agricultores ativos do grupo (informativo, art. 79 §2º)
   */
  public record VisitorSuggestion(
      List<SuggestedVisitor> eligible,
      List<ExcludedVisitor> excluded,
      List<PersonRef> collaborators,
      Integer samplingMinimum) {}
}
