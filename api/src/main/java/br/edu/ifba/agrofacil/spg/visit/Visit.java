// Visita do SPG a uma propriedade: de pares ou de verificação, com o resultado (RN29–RN36).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.common.domain.GeoPointValue;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.persistence.SyncableEntity;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.type.SqlTypes;

/**
 * Os participantes ficam em {@link VisitParticipant}. Mudar participantes "toca" a visita, para o
 * pull levar a versão nova a todos os envolvidos.
 */
@Entity
@Table(name = "visit")
@Audited
public class Visit extends SyncableEntity {

  /** Origem do resultado: app do responsável ou digitado no painel (ADR-16). */
  public enum ResultSource {
    MOBILE,
    WEB
  }

  private UUID associationId;
  private UUID propertyId;
  private UUID groupId;
  private UUID certificationId;

  @Enumerated(EnumType.STRING)
  private VisitType type;

  private boolean unannounced;

  @Enumerated(EnumType.STRING)
  private VisitStatus status;

  private LocalDate plannedOn;
  private String cancelReason;
  private UUID recorderParticipantId;
  private LocalDate doneOn;

  /** JSON com as respostas: [{itemCode, answer, note}]. */
  @JdbcTypeCode(SqlTypes.JSON)
  private String answers;

  private Boolean issuesFound;
  private String improvementPoints;
  private String recommendations;
  private String notes;
  @Embedded private GeoPointValue location;

  @Enumerated(EnumType.STRING)
  private ResultSource resultSource;

  private UUID resultRecordedBy;
  private Instant resultRecordedAt;

  /** Resumo da propriedade para os visitadores (ADR-17); JSON do {@code VisitBrief}. */
  @NotAudited
  @JdbcTypeCode(SqlTypes.JSON)
  private String brief;

  @NotAudited private Instant briefUpdatedAt;

  private UUID createdBy;
  private UUID updatedBy;

  protected Visit() {}

  public Visit(
      UUID associationId,
      UUID propertyId,
      UUID groupId,
      UUID certificationId,
      VisitType type,
      UUID createdBy) {
    super(UUID.randomUUID());
    this.associationId = associationId;
    this.propertyId = propertyId;
    this.groupId = groupId;
    this.certificationId = certificationId;
    this.type = type;
    this.status = VisitStatus.PLANNED;
    this.answers = "[]";
    this.createdBy = createdBy;
    this.updatedBy = createdBy;
  }

  /** Dados da agenda; só em {@code PLANNED} (RN34). */
  public void schedule(LocalDate plannedOn, boolean unannounced, UUID by) {
    requirePlanned();
    if (unannounced && type != VisitType.VERIFICATION) {
      throw new ApiException(ErrorCode.UNANNOUNCED_ONLY_VERIFICATION); // RN31
    }
    this.plannedOn = plannedOn;
    this.unannounced = unannounced;
    this.updatedBy = by;
  }

  public void setRecorderParticipantId(UUID recorderParticipantId) {
    this.recorderParticipantId = recorderParticipantId;
  }

  /** RN34: cancelar exige motivo e só vale para visita marcada. */
  public void cancel(String reason, UUID by) {
    requirePlanned();
    this.status = VisitStatus.CANCELLED;
    this.cancelReason = reason;
    this.updatedBy = by;
  }

  /** Grava o resultado e conclui a visita (RN35). As validações ficam no serviço. */
  public void recordResult(
      LocalDate doneOn,
      String answersJson,
      boolean issuesFound,
      String improvementPoints,
      String recommendations,
      String notes,
      GeoPointValue location,
      ResultSource source,
      UUID by,
      Instant when) {
    if (status == VisitStatus.CANCELLED) {
      throw new ApiException(ErrorCode.VISIT_NOT_EDITABLE);
    }
    this.status = VisitStatus.DONE;
    this.doneOn = doneOn;
    this.answers = answersJson;
    this.issuesFound = issuesFound;
    this.improvementPoints = improvementPoints;
    this.recommendations = recommendations;
    this.notes = notes;
    this.location = location;
    this.resultSource = source;
    this.resultRecordedBy = by;
    this.resultRecordedAt = when;
    this.updatedBy = by;
  }

  /**
   * Troca o resumo só se o conteúdo mudou: uma escrita à toa muda o sync_seq e força o pull nos
   * celulares (doc 2 §1.15.3).
   *
   * @return true se mudou
   */
  public boolean refreshBrief(String json, Instant when) {
    if (json.equals(brief)) {
      return false;
    }
    this.brief = json;
    this.briefUpdatedAt = when;
    return true;
  }

  private void requirePlanned() {
    if (status != VisitStatus.PLANNED) {
      throw new ApiException(ErrorCode.VISIT_NOT_EDITABLE);
    }
  }

  public boolean isPlanned() {
    return status == VisitStatus.PLANNED;
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public UUID getPropertyId() {
    return propertyId;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public UUID getCertificationId() {
    return certificationId;
  }

  public VisitType getType() {
    return type;
  }

  public boolean isUnannounced() {
    return unannounced;
  }

  public VisitStatus getStatus() {
    return status;
  }

  public LocalDate getPlannedOn() {
    return plannedOn;
  }

  public String getCancelReason() {
    return cancelReason;
  }

  public UUID getRecorderParticipantId() {
    return recorderParticipantId;
  }

  public LocalDate getDoneOn() {
    return doneOn;
  }

  public String getAnswers() {
    return answers;
  }

  public Boolean getIssuesFound() {
    return issuesFound;
  }

  public String getImprovementPoints() {
    return improvementPoints;
  }

  public String getRecommendations() {
    return recommendations;
  }

  public String getNotes() {
    return notes;
  }

  public GeoPointValue getLocation() {
    return location;
  }

  public ResultSource getResultSource() {
    return resultSource;
  }

  public UUID getResultRecordedBy() {
    return resultRecordedBy;
  }

  public Instant getResultRecordedAt() {
    return resultRecordedAt;
  }

  public String getBrief() {
    return brief;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }
}
