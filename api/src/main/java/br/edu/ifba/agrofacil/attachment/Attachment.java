// Metadados de um anexo (foto, áudio, PDF); o binário fica no FileStorage (doc 1 §3.6).
package br.edu.ifba.agrofacil.attachment;

import br.edu.ifba.agrofacil.common.persistence.SyncableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** O metadado chega primeiro (push); o binário depois, em PUT /files/{id}/content. */
@Entity
@Table(name = "attachment")
public class Attachment extends SyncableEntity {

  public enum OwnerType {
    RECORD,
    PROPERTY,
    CERTIFICATION,
    ANNOUNCEMENT,
    VISIT,
    DOCUMENT
  }

  public enum Kind {
    PHOTO,
    AUDIO,
    DOCUMENT
  }

  private UUID associationId;

  @Enumerated(EnumType.STRING)
  private OwnerType ownerType;

  private UUID ownerId;

  @Enumerated(EnumType.STRING)
  private Kind kind;

  private String contentType;
  private Long sizeBytes;

  @JdbcTypeCode(SqlTypes.CHAR) // coluna char(n)
  private String sha256; // informado pelo cliente; o servidor confere no upload

  private String storageKey; // yyyy/MM/<id>, nunca o nome enviado
  private Integer durationSeconds;
  private Instant uploadedAt; // nulo enquanto o binário não chegou
  private UUID createdBy;

  protected Attachment() {}

  public Attachment(
      UUID id, UUID associationId, OwnerType ownerType, UUID ownerId, Kind kind, UUID createdBy) {
    super(id);
    this.associationId = associationId;
    this.ownerType = ownerType;
    this.ownerId = ownerId;
    this.kind = kind;
    this.createdBy = createdBy;
  }

  public void describe(String contentType, Long sizeBytes, String sha256, Integer durationSeconds) {
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.sha256 = sha256;
    this.durationSeconds = durationSeconds;
  }

  public void markUploaded(String storageKey, long size, String sha256, Instant when) {
    this.storageKey = storageKey;
    this.sizeBytes = size;
    this.sha256 = sha256;
    this.uploadedAt = when;
  }

  public boolean isUploaded() {
    return uploadedAt != null;
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public OwnerType getOwnerType() {
    return ownerType;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public Kind getKind() {
    return kind;
  }

  public String getContentType() {
    return contentType;
  }

  public Long getSizeBytes() {
    return sizeBytes;
  }

  public String getSha256() {
    return sha256;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public Integer getDurationSeconds() {
    return durationSeconds;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }
}
