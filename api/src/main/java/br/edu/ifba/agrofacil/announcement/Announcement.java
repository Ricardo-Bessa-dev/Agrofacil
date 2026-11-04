// Comunicado ou dica da associação para todos os agricultores ou para um grupo do SPG (RN23).
package br.edu.ifba.agrofacil.announcement;

import br.edu.ifba.agrofacil.common.persistence.SyncableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** O texto é puro (sem HTML): o app e o painel nunca o interpretam como marcação. */
@Entity
@Table(name = "announcement")
public class Announcement extends SyncableEntity {

  private UUID associationId;

  @Enumerated(EnumType.STRING)
  private AnnouncementType type;

  private String title;
  private String body;
  private UUID groupId; // nulo = todos os agricultores da associação
  private Instant publishedAt;
  private UUID createdBy;

  protected Announcement() {}

  public Announcement(UUID associationId, UUID groupId, Instant publishedAt, UUID createdBy) {
    super(UUID.randomUUID());
    this.associationId = associationId;
    this.groupId = groupId;
    this.publishedAt = publishedAt;
    this.createdBy = createdBy;
  }

  /** Muda o conteúdo; o público (grupo) é fixo depois de publicar. */
  public void update(AnnouncementType type, String title, String body) {
    this.type = type;
    this.title = title;
    this.body = body;
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public AnnouncementType getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }
}
