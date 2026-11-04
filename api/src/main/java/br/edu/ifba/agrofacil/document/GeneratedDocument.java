// Documento do SPG gerado em PDF, com código de verificação, hash e os dados usados (RN37).
package br.edu.ifba.agrofacil.document;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Documentos não são excluídos: são cancelados com motivo ou substituídos por um novo. */
@Entity
@Table(name = "generated_document")
public class GeneratedDocument {

  public enum Status {
    GENERATED,
    SIGNED,
    SUPERSEDED,
    CANCELLED
  }

  @Id private UUID id;
  private UUID associationId;

  @Enumerated(EnumType.STRING)
  private DocumentKind kind;

  private String templateVersion;
  private String title;
  private UUID farmerId;
  private UUID propertyId;
  private UUID groupId;
  private UUID visitId;
  private UUID certificationId;
  private LocalDate periodFrom;
  private LocalDate periodTo;

  @JdbcTypeCode(SqlTypes.CHAR) // coluna char(8)
  private String verificationCode;

  @JdbcTypeCode(SqlTypes.CHAR) // coluna char(64)
  private String sha256;

  private String storageKey;
  private long sizeBytes;

  @JdbcTypeCode(SqlTypes.JSON)
  private String dataSnapshot;

  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<String> warnings = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  private Status status;

  private UUID signedAttachmentId;
  private UUID supersededById;
  private String cancelReason;
  private UUID generatedBy;
  private Instant generatedAt;

  protected GeneratedDocument() {}

  public GeneratedDocument(
      UUID id,
      UUID associationId,
      DocumentKind kind,
      String templateVersion,
      String title,
      DocumentSubject subject,
      UUID generatedBy,
      Instant generatedAt) {
    this.id = id;
    this.associationId = associationId;
    this.kind = kind;
    this.templateVersion = templateVersion;
    this.title = title;
    this.farmerId = subject.farmerId();
    this.propertyId = subject.propertyId();
    this.groupId = subject.groupId();
    this.visitId = subject.visitId();
    this.certificationId = subject.certificationId();
    this.periodFrom = subject.from();
    this.periodTo = subject.to();
    this.status = Status.GENERATED;
    this.generatedBy = generatedBy;
    this.generatedAt = generatedAt;
  }

  /** Conteúdo gerado: arquivo guardado, hash, código e os dados usados. */
  public void store(
      String code,
      String sha256,
      String storageKey,
      long sizeBytes,
      String dataJson,
      List<String> warnings) {
    this.verificationCode = code;
    this.sha256 = sha256;
    this.storageKey = storageKey;
    this.sizeBytes = sizeBytes;
    this.dataSnapshot = dataJson;
    this.warnings = new ArrayList<>(warnings);
  }

  /** RN37: o novo documento do mesmo tipo e assunto substitui este. */
  public void supersede(UUID by) {
    this.status = Status.SUPERSEDED;
    this.supersededById = by;
  }

  public void markSigned(UUID attachmentId) {
    if (status == Status.CANCELLED || status == Status.SUPERSEDED) {
      throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
    }
    this.status = Status.SIGNED;
    this.signedAttachmentId = attachmentId;
  }

  public void cancel(String reason) {
    if (status == Status.CANCELLED) {
      throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
    }
    this.status = Status.CANCELLED;
    this.cancelReason = reason;
  }

  public DocumentSubject subject() {
    return new DocumentSubject(
        farmerId, propertyId, groupId, visitId, certificationId, periodFrom, periodTo);
  }

  public UUID getId() {
    return id;
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public DocumentKind getKind() {
    return kind;
  }

  public String getTemplateVersion() {
    return templateVersion;
  }

  public String getTitle() {
    return title;
  }

  public String getVerificationCode() {
    return verificationCode;
  }

  public String getSha256() {
    return sha256;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public List<String> getWarnings() {
    return List.copyOf(warnings);
  }

  public Status getStatus() {
    return status;
  }

  public UUID getSignedAttachmentId() {
    return signedAttachmentId;
  }

  public UUID getSupersededById() {
    return supersededById;
  }

  public String getCancelReason() {
    return cancelReason;
  }

  public UUID getGeneratedBy() {
    return generatedBy;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }
}
