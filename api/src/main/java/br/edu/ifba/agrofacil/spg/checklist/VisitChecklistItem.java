// Pergunta do roteiro da visita (modelo IN19-v1); association_id nulo = modelo padrão (doc 1 §6.6).
package br.edu.ifba.agrofacil.spg.checklist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * As perguntas são positivas: "Sim" é sempre o esperado. O {@code code} não muda depois de criado,
 * porque as respostas das visitas apontam para ele.
 */
@Entity
@Table(name = "visit_checklist_item")
public class VisitChecklistItem {

  public enum AppliesTo {
    PEER,
    VERIFICATION,
    BOTH
  }

  @Id private UUID id;
  private String code;
  private UUID associationId;

  @Enumerated(EnumType.STRING)
  private AppliesTo appliesTo;

  private String question;
  private String formalText;
  private String help;
  private String legalReference;
  private int sortOrder;
  private String templateVersion;
  private boolean active;
  private Instant updatedAt;

  /** Preenchido pelo trigger do banco (ADR-09); só leitura para o JPA. */
  @Column(insertable = false, updatable = false)
  private Long syncSeq;

  protected VisitChecklistItem() {}

  public VisitChecklistItem(UUID associationId, String code, String templateVersion) {
    this.id = UUID.randomUUID();
    this.associationId = associationId;
    this.code = code;
    this.templateVersion = templateVersion;
  }

  /** Cópia da associação de um item do modelo padrão, para poder ajustá-lo. */
  public VisitChecklistItem copyFor(UUID associationId) {
    var copy = new VisitChecklistItem(associationId, code, templateVersion);
    copy.update(appliesTo, question, formalText, help, legalReference, sortOrder, active);
    return copy;
  }

  public void update(
      AppliesTo appliesTo,
      String question,
      String formalText,
      String help,
      String legalReference,
      int sortOrder,
      boolean active) {
    this.appliesTo = appliesTo;
    this.question = question;
    this.formalText = formalText;
    this.help = help;
    this.legalReference = legalReference;
    this.sortOrder = sortOrder;
    this.active = active;
  }

  @PrePersist
  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }

  /** Vale para o tipo de visita (PEER ou VERIFICATION). */
  public boolean appliesTo(String visitType) {
    return appliesTo == AppliesTo.BOTH || appliesTo.name().equals(visitType);
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public AppliesTo getAppliesTo() {
    return appliesTo;
  }

  public String getQuestion() {
    return question;
  }

  public String getFormalText() {
    return formalText;
  }

  public String getHelp() {
    return help;
  }

  public String getLegalReference() {
    return legalReference;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public String getTemplateVersion() {
    return templateVersion;
  }

  public boolean isActive() {
    return active;
  }

  public Long getSyncSeq() {
    return syncSeq;
  }
}
