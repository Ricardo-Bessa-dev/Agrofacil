// Notificação no formato do contrato (doc 1 §3.12).
package br.edu.ifba.agrofacil.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationView(
    UUID id,
    NotificationType type,
    String title,
    String body,
    Map<String, String> data,
    Instant readAt,
    Instant createdAt) {}
