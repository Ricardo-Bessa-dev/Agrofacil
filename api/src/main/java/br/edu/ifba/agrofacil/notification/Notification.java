// Notificação de um usuário; a do agricultor chega ao app pelo pull e por push.
package br.edu.ifba.agrofacil.notification;

import br.edu.ifba.agrofacil.common.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

  private UUID userId;

  @Enumerated(EnumType.STRING)
  private NotificationType type;

  private String title;
  private String body;

  /** JSON com os ids para abrir a tela, ex.: {"recordId": "..."}. */
  @JdbcTypeCode(SqlTypes.JSON)
  private String data;

  private Instant readAt;

  protected Notification() {}

  public Notification(
      UUID userId, NotificationType type, String title, String body, String dataJson) {
    super(UUID.randomUUID());
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.body = body;
    this.data = dataJson;
  }

  /** Marca como lida; ler de novo não muda a data da primeira leitura. */
  public void markRead(Instant when) {
    if (readAt == null) {
      readAt = when;
    }
  }

  public UUID getUserId() {
    return userId;
  }

  public NotificationType getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public String getData() {
    return data;
  }

  public Instant getReadAt() {
    return readAt;
  }
}
