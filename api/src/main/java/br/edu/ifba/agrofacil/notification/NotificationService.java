// Grava notificações por usuário e dispara o push depois do commit (doc 2 §1.12.2).
package br.edu.ifba.agrofacil.notification;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationService {

  private static final TypeReference<LinkedHashMap<String, String>> DATA_TYPE =
      new TypeReference<>() {};

  private final NotificationRepository repository;
  private final ApplicationEventPublisher events;
  private final ObjectMapper mapper;
  private final Clock clock;

  public NotificationService(
      NotificationRepository repository,
      ApplicationEventPublisher events,
      ObjectMapper mapper,
      Clock clock) {
    this.repository = repository;
    this.events = events;
    this.mapper = mapper;
    this.clock = clock;
  }

  /**
   * Cria uma notificação para cada usuário, na transação atual. O push só é enviado se a transação
   * fizer commit.
   *
   * @param data ids que o app usa para abrir a tela certa, ex.: {@code recordId}
   */
  @Transactional
  public void notify(
      Collection<UUID> userIds,
      NotificationType type,
      String title,
      String body,
      Map<String, String> data) {
    if (userIds.isEmpty()) {
      return;
    }
    String json = mapper.writeValueAsString(data);
    var saved =
        repository.saveAll(
            userIds.stream().map(id -> new Notification(id, type, title, body, json)).toList());
    events.publishEvent(new NotificationsCreated(saved.stream().map(Notification::getId).toList()));
  }

  /** Lista as notificações do coordenador (as do agricultor vão pelo pull). */
  @Transactional(readOnly = true)
  public PageResponse<NotificationView> list(CurrentUser user, boolean unreadOnly, Pageable page) {
    var result =
        unreadOnly
            ? repository.findByUserIdAndReadAtIsNull(user.id(), page)
            : repository.findByUserId(user.id(), page);
    return PageResponse.of(result, this::toView);
  }

  /** Marca uma notificação do próprio usuário como lida. */
  @Transactional
  public void markRead(CurrentUser user, UUID id) {
    repository
        .findByIdAndUserId(id, user.id())
        .orElseThrow(ApiException::notFound)
        .markRead(clock.instant());
  }

  @Transactional
  public void markAllRead(CurrentUser user) {
    repository.markAllRead(user.id(), clock.instant());
  }

  public NotificationView toView(Notification n) {
    Map<String, String> data =
        n.getData() == null ? Map.of() : mapper.readValue(n.getData(), DATA_TYPE);
    return new NotificationView(
        n.getId(), n.getType(), n.getTitle(), n.getBody(), data, n.getReadAt(), n.getCreatedAt());
  }
}
