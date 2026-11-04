// Envia as notificações como push pelo Expo, depois do commit e fora da thread da requisição.
package br.edu.ifba.agrofacil.notification;

import br.edu.ifba.agrofacil.config.AppProperties;
import br.edu.ifba.agrofacil.user.Device;
import br.edu.ifba.agrofacil.user.DeviceRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Falha no push não é erro de negócio: vai para o log, e a notificação continua chegando ao app
 * pelo pull (doc 2 §1.12.2).
 */
@Component
public class ExpoPushSender {

  private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);
  private static final int BATCH_SIZE = 100; // limite do Expo por requisição
  private static final String NOT_REGISTERED = "DeviceNotRegistered";
  private static final TypeReference<Map<String, String>> DATA_TYPE = new TypeReference<>() {};

  private final NotificationRepository notifications;
  private final DeviceRepository devices;
  private final AppProperties props;
  private final ObjectMapper mapper;
  private final RestClient http;

  public ExpoPushSender(
      NotificationRepository notifications,
      DeviceRepository devices,
      AppProperties props,
      ObjectMapper mapper) {
    this.notifications = notifications;
    this.devices = devices;
    this.props = props;
    this.mapper = mapper;
    this.http = RestClient.create();
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNotificationsCreated(NotificationsCreated event) {
    try {
      send(notifications.findAllById(event.notificationIds()));
    } catch (RuntimeException e) {
      log.warn("Falha ao enviar push: {}", e.getMessage());
    }
  }

  private void send(List<Notification> list) {
    var userIds = list.stream().map(Notification::getUserId).distinct().toList();
    Map<UUID, List<Device>> devicesByUser =
        devices.findByUserIdInAndRevokedAtIsNull(userIds).stream()
            .filter(d -> d.getPushToken() != null)
            .collect(Collectors.groupingBy(Device::getUserId));
    var messages = new ArrayList<Map<String, Object>>();
    var targets = new ArrayList<Device>();
    for (var n : list) {
      for (var device : devicesByUser.getOrDefault(n.getUserId(), List.of())) {
        messages.add(message(device.getPushToken(), n));
        targets.add(device);
      }
    }
    for (int start = 0; start < messages.size(); start += BATCH_SIZE) {
      int end = Math.min(start + BATCH_SIZE, messages.size());
      postBatch(messages.subList(start, end), targets.subList(start, end));
    }
  }

  private Map<String, Object> message(String token, Notification n) {
    var data = new LinkedHashMap<String, String>();
    data.put("type", n.getType().name());
    data.putAll(n.getData() == null ? Map.of() : mapper.readValue(n.getData(), DATA_TYPE));
    var message = new LinkedHashMap<String, Object>();
    message.put("to", token);
    message.put("title", n.getTitle());
    message.put("body", n.getBody());
    message.put("data", data);
    message.put("sound", "default");
    return message;
  }

  /** Envia um lote e limpa o token dos aparelhos que o Expo diz não existirem mais. */
  private void postBatch(List<Map<String, Object>> batch, List<Device> targets) {
    var request = http.post().uri(props.push().expoUrl()).contentType(MediaType.APPLICATION_JSON);
    String token = props.push().expoAccessToken();
    if (token != null && !token.isBlank()) {
      request = request.header("Authorization", "Bearer " + token);
    }
    JsonNode response = request.body(batch).retrieve().body(JsonNode.class);
    JsonNode tickets = response == null ? null : response.get("data");
    if (tickets == null || !tickets.isArray()) {
      return;
    }
    Map<UUID, Device> toClear =
        targets.stream().collect(Collectors.toMap(Device::getId, Function.identity(), (a, b) -> a));
    for (int i = 0; i < tickets.size() && i < targets.size(); i++) {
      JsonNode details = tickets.get(i).get("details");
      if (details != null && NOT_REGISTERED.equals(details.path("error").asString(""))) {
        var device = toClear.get(targets.get(i).getId());
        device.setPushToken(null);
        devices.save(device);
      }
    }
  }
}
