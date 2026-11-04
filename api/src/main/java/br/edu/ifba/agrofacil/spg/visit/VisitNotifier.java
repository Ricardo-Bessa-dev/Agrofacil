// Avisos das visitas que saem na hora: marcar, mudar, cancelar e resultado pelo app (RN30, RN31).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.common.domain.BrFormat;
import br.edu.ifba.agrofacil.common.security.Role;
import br.edu.ifba.agrofacil.notification.NotificationService;
import br.edu.ifba.agrofacil.notification.NotificationType;
import br.edu.ifba.agrofacil.user.AppUser;
import br.edu.ifba.agrofacil.user.UserRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Quem recebe é decidido num lugar só ({@link #recipients}): o visitado nunca recebe aviso de
 * visita sem aviso antes de ela ser concluída (RN31).
 */
@Component
public class VisitNotifier {

  private final NotificationService notifications;
  private final UserRepository users;

  public VisitNotifier(NotificationService notifications, UserRepository users) {
    this.notifications = notifications;
    this.users = users;
  }

  /** Dados da visita usados nos textos. */
  public record Context(
      Visit visit, String propertyName, UUID visitedFarmerId, String visitedName) {}

  /**
   * Agricultores avisados sobre a visita: os participantes e, se a visita for com aviso (ou já
   * concluída), o visitado.
   */
  public static Set<UUID> recipients(Context ctx, Collection<UUID> participantFarmerIds) {
    var result = new HashSet<>(participantFarmerIds);
    if (!ctx.visit().isUnannounced() || ctx.visit().getStatus() == VisitStatus.DONE) {
      result.add(ctx.visitedFarmerId());
    } else {
      result.remove(ctx.visitedFarmerId());
    }
    return result;
  }

  /** Visita marcada: visitadores e, se for com aviso, o visitado. */
  public void scheduled(
      Context ctx, Collection<UUID> farmerParticipants, List<String> visitorNames) {
    String when = BrFormat.weekdayAndDay(ctx.visit().getPlannedOn());
    notifications.notify(
        farmerParticipants,
        NotificationType.VISIT_ASSIGNED,
        "Você foi escalado para uma visita",
        "Você vai visitar o %s, de %s: %s.".formatted(ctx.propertyName(), ctx.visitedName(), when),
        data(ctx));
    if (!ctx.visit().isUnannounced()) {
      String who = ctx.visit().getType() == VisitType.PEER ? "do grupo" : "da comissão";
      notifications.notify(
          List.of(ctx.visitedFarmerId()),
          NotificationType.VISIT_SCHEDULED,
          "Visita %s na sua roça".formatted(who),
          "Visita %s na sua roça: %s, com %s."
              .formatted(who, when, String.join(" e ", visitorNames)),
          data(ctx));
    }
  }

  /** Data ou participantes mudaram: avisa quem saiu, quem entrou e quem continua. */
  public void changed(
      Context ctx,
      Set<UUID> removed,
      Set<UUID> added,
      Set<UUID> kept,
      boolean dateChanged,
      List<String> visitorNames) {
    String when = BrFormat.weekdayAndDay(ctx.visit().getPlannedOn());
    notifications.notify(
        removed,
        NotificationType.VISIT_CHANGED,
        "Mudança na visita",
        "Você não está mais na visita ao %s.".formatted(ctx.propertyName()),
        data(ctx));
    if (!added.isEmpty()) {
      notifications.notify(
          added,
          NotificationType.VISIT_ASSIGNED,
          "Você foi escalado para uma visita",
          "Você vai visitar o %s, de %s: %s."
              .formatted(ctx.propertyName(), ctx.visitedName(), when),
          data(ctx));
    }
    if (dateChanged) {
      notifications.notify(
          recipients(ctx, kept),
          NotificationType.VISIT_CHANGED,
          "A visita mudou de data",
          "A visita ao %s mudou para %s.".formatted(ctx.propertyName(), when),
          data(ctx));
    } else if (!ctx.visit().isUnannounced() && (!added.isEmpty() || !removed.isEmpty())) {
      notifications.notify(
          List.of(ctx.visitedFarmerId()),
          NotificationType.VISIT_CHANGED,
          "Mudança na visita",
          "A visita na sua roça (%s) agora é com %s."
              .formatted(when, String.join(" e ", visitorNames)),
          data(ctx));
    }
  }

  public void cancelled(Context ctx, Collection<UUID> farmerParticipants) {
    notifications.notify(
        recipients(ctx, farmerParticipants),
        NotificationType.VISIT_CANCELLED,
        "Visita cancelada",
        "A visita ao %s de %s foi cancelada."
            .formatted(ctx.propertyName(), BrFormat.weekdayAndDay(ctx.visit().getPlannedOn())),
        data(ctx));
  }

  /** O responsável registrou o resultado pelo app: avisa os coordenadores. */
  public void resultFromApp(Context ctx, String recorderName) {
    notifications.notify(
        coordinators(ctx.visit().getAssociationId()),
        NotificationType.VISIT_RESULT_RECORDED,
        "Resultado de visita recebido",
        "%s registrou a visita ao %s.".formatted(recorderName, ctx.propertyName()),
        data(ctx));
  }

  /** Coordenadores ativos da associação. */
  public List<UUID> coordinators(UUID associationId) {
    return users
        .findByAssociationIdAndRoleAndDeletedAtIsNullOrderByName(associationId, Role.COORDINATOR)
        .stream()
        .filter(AppUser::isActive)
        .map(AppUser::getId)
        .toList();
  }

  public static Map<String, String> data(Context ctx) {
    return Map.of(
        "visitId", ctx.visit().getId().toString(),
        "propertyId", ctx.visit().getPropertyId().toString());
  }
}
