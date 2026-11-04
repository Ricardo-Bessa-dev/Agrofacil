// Formatos de /sync/push e /sync/pull (doc 1 §3.5).
package br.edu.ifba.agrofacil.sync;

import br.edu.ifba.agrofacil.announcement.AnnouncementView;
import br.edu.ifba.agrofacil.attachment.AttachmentView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.CropView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.FarmInputView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.UnitView;
import br.edu.ifba.agrofacil.certification.CertificationView;
import br.edu.ifba.agrofacil.common.error.FieldErrorItem;
import br.edu.ifba.agrofacil.common.security.Role;
import br.edu.ifba.agrofacil.correction.CorrectionRequestView;
import br.edu.ifba.agrofacil.notification.NotificationView;
import br.edu.ifba.agrofacil.property.PlotView;
import br.edu.ifba.agrofacil.property.PropertyView;
import br.edu.ifba.agrofacil.property.WorkerView;
import br.edu.ifba.agrofacil.record.RecordView;
import br.edu.ifba.agrofacil.spg.checklist.ChecklistDtos;
import br.edu.ifba.agrofacil.spg.visit.VisitDtos;
import br.edu.ifba.agrofacil.user.Me;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class SyncDtos {

  /** Máximo de mutações por lote (doc 1 §3.5.1). */
  public static final int MAX_MUTATIONS = 100;

  private SyncDtos() {}

  public enum Op {
    UPSERT,
    DELETE
  }

  public enum ResultStatus {
    APPLIED,
    REJECTED
  }

  public record PushRequest(
      @NotNull UUID deviceId,
      @NotNull @Size(max = MAX_MUTATIONS) List<@Valid Mutation> mutations) {}

  /**
   * @param entity {@code record}, {@code property}, {@code attachment}, {@code notification} ou
   *     {@code visit}
   * @param baseVersion só informativo: o último a chegar vence (ADR-08)
   * @param data estado completo da entidade; ausente em DELETE
   */
  public record Mutation(
      @NotNull UUID mutationId,
      @NotNull String entity,
      @NotNull Op op,
      @NotNull UUID entityId,
      Long baseVersion,
      Instant clientTimestamp,
      JsonNode data) {}

  public record MutationError(String code, String message, List<FieldErrorItem> errors) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MutationResult(
      UUID mutationId, ResultStatus status, UUID entityId, Long version, MutationError error) {}

  public record PushResponse(List<MutationResult> results, Instant serverTime) {}

  public record Person(UUID id, String name, Role role) {}

  public record Tombstone(String entity, UUID id) {}

  public record Changes(
      Me me,
      Me.MeAssociation association,
      List<Person> people,
      List<PropertyView> properties,
      List<PlotView> plots,
      List<WorkerView> workers,
      List<CertificationView> certifications,
      List<RecordView> records,
      List<AttachmentView> attachments,
      List<CorrectionRequestView> correctionRequests,
      List<AnnouncementView> announcements,
      List<NotificationView> notifications,
      List<CropView> crops,
      List<FarmInputView> farmInputs,
      List<UnitView> units,
      List<VisitDtos.VisitView> visits,
      List<ChecklistDtos.View> visitChecklistItems) {}

  /**
   * @param cursor guardar e enviar no próximo pull
   * @param hasMore se true, chamar de novo imediatamente
   * @param reset se true, apagar os dados vindos do servidor e puxar com cursor "0"
   */
  public record PullResponse(
      String cursor,
      boolean hasMore,
      boolean reset,
      Instant serverTime,
      Changes changes,
      List<Tombstone> deleted) {}
}
