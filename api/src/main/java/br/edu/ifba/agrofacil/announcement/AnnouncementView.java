// Comunicado no formato do contrato (doc 1 §3.12), igual no painel e no pull do app.
package br.edu.ifba.agrofacil.announcement;

import java.time.Instant;
import java.util.UUID;

/**
 * @param imageAttachmentId foto anexada ao comunicado, se houver
 */
public record AnnouncementView(
    UUID id,
    AnnouncementType type,
    String title,
    String body,
    UUID groupId,
    UUID imageAttachmentId,
    Instant publishedAt,
    UUID createdById,
    Long version) {

  public AnnouncementWrite toWrite() {
    return new AnnouncementWrite(type, title, body, groupId, version);
  }
}
