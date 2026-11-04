// Anexo no formato do contrato (doc 1 §3.6).
package br.edu.ifba.agrofacil.attachment;

import java.time.Instant;
import java.util.UUID;

/**
 * @param uploaded false enquanto o binário não chegou
 */
public record AttachmentView(
    UUID id,
    Attachment.OwnerType ownerType,
    UUID ownerId,
    Attachment.Kind kind,
    String contentType,
    Long sizeBytes,
    String sha256,
    Integer durationSeconds,
    boolean uploaded,
    Instant createdAt) {

  public static AttachmentView of(Attachment a) {
    return new AttachmentView(
        a.getId(),
        a.getOwnerType(),
        a.getOwnerId(),
        a.getKind(),
        a.getContentType(),
        a.getSizeBytes(),
        a.getSha256(),
        a.getDurationSeconds(),
        a.isUploaded(),
        a.getCreatedAt());
  }
}
