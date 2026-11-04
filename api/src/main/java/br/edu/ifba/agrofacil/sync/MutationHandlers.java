// Handlers das mutações do app: registros, propriedades, anexos e leitura de notificações.
package br.edu.ifba.agrofacil.sync;

import br.edu.ifba.agrofacil.attachment.AttachmentService;
import br.edu.ifba.agrofacil.attachment.AttachmentWrite;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.notification.NotificationRepository;
import br.edu.ifba.agrofacil.property.PropertyService;
import br.edu.ifba.agrofacil.property.PropertyWrite;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordSource;
import br.edu.ifba.agrofacil.record.RecordService;
import br.edu.ifba.agrofacil.record.RecordWrite;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cada handler converte o {@code data} no DTO certo e reaproveita o serviço do painel. */
@Configuration
public class MutationHandlers {

  /** Corpo da mutação {@code notification}: só marca como lida. */
  public record NotificationRead(@NotNull Instant readAt) {}

  @Bean
  MutationHandler recordMutationHandler(RecordService records, JsonPatch json) {
    return new MutationHandler() {
      @Override
      public String entity() {
        return "record";
      }

      @Override
      public Long apply(CurrentUser user, SyncDtos.Mutation m) {
        if (m.op() == SyncDtos.Op.DELETE) {
          return records.delete(user, m.entityId()).getVersion();
        }
        var write = json.convertAndValidate(requireData(m), RecordWrite.class);
        ensureSameId(write.id(), m.entityId());
        return records.upsert(user, m.entityId(), write, RecordSource.MOBILE).getVersion();
      }
    };
  }

  @Bean
  MutationHandler propertyMutationHandler(PropertyService properties, JsonPatch json) {
    return new MutationHandler() {
      @Override
      public String entity() {
        return "property";
      }

      @Override
      public Long apply(CurrentUser user, SyncDtos.Mutation m) {
        if (m.op() == SyncDtos.Op.DELETE) {
          return properties.deleteFromFarmer(user, m.entityId()).getVersion();
        }
        var write = json.convertAndValidate(requireData(m), PropertyWrite.class);
        ensureSameId(write.id(), m.entityId());
        return properties.upsertFromFarmer(user, m.entityId(), write).getVersion();
      }
    };
  }

  @Bean
  MutationHandler attachmentMutationHandler(AttachmentService attachments, JsonPatch json) {
    return new MutationHandler() {
      @Override
      public String entity() {
        return "attachment";
      }

      @Override
      public Long apply(CurrentUser user, SyncDtos.Mutation m) {
        if (m.op() == SyncDtos.Op.DELETE) {
          return attachments.delete(user, m.entityId()).getVersion();
        }
        var write = json.convertAndValidate(requireData(m), AttachmentWrite.class);
        ensureSameId(write.id(), m.entityId());
        return attachments.upsertFromFarmer(user, m.entityId(), write).getVersion();
      }
    };
  }

  @Bean
  MutationHandler notificationMutationHandler(
      NotificationRepository notifications, JsonPatch json, Clock clock) {
    return new MutationHandler() {
      @Override
      public String entity() {
        return "notification";
      }

      @Override
      public Long apply(CurrentUser user, SyncDtos.Mutation m) {
        if (m.op() == SyncDtos.Op.DELETE) {
          throw ApiException.validation("op", "NOT_ALLOWED", "Avisos não podem ser apagados.");
        }
        var read = json.convertAndValidate(requireData(m), NotificationRead.class);
        var notification =
            notifications
                .findByIdAndUserId(m.entityId(), user.id())
                .orElseThrow(ApiException::notFound);
        // A hora do aparelho pode estar adiantada: nunca grava leitura no futuro.
        Instant now = clock.instant();
        notification.markRead(read.readAt().isAfter(now) ? now : read.readAt());
        return notifications.saveAndFlush(notification).getVersion();
      }
    };
  }

  private static tools.jackson.databind.JsonNode requireData(SyncDtos.Mutation m) {
    if (m.data() == null || !m.data().isObject()) {
      throw ApiException.validation("data", "REQUIRED", "Faltam os dados da mutação.");
    }
    return m.data();
  }

  private static void ensureSameId(UUID dataId, UUID entityId) {
    if (dataId != null && !dataId.equals(entityId)) {
      throw ApiException.validation("data.id", "ID_MISMATCH", "O id dos dados não confere.");
    }
  }
}
