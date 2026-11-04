// Evento publicado quando notificações são gravadas; o push sai depois do commit.
package br.edu.ifba.agrofacil.notification;

import java.util.List;
import java.util.UUID;

/**
 * @param notificationIds notificações recém-criadas, que viram push nos aparelhos ativos
 */
public record NotificationsCreated(List<UUID> notificationIds) {}
