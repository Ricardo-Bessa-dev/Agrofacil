// Avisos diários do SPG: período de visitas aberto, véspera da visita e resultado atrasado (RN30).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.association.Association;
import br.edu.ifba.agrofacil.association.AssociationRepository;
import br.edu.ifba.agrofacil.certification.CertificationReader;
import br.edu.ifba.agrofacil.common.domain.BrFormat;
import br.edu.ifba.agrofacil.mail.MailComposer;
import br.edu.ifba.agrofacil.mail.MailService;
import br.edu.ifba.agrofacil.notification.AlertLog;
import br.edu.ifba.agrofacil.notification.NotificationService;
import br.edu.ifba.agrofacil.notification.NotificationType;
import br.edu.ifba.agrofacil.property.PropertyRepository;
import br.edu.ifba.agrofacil.user.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Roda às 07:05, logo depois dos alertas de certificação. Quem recebe vem do {@link
 * VisitNotifier#recipients}: o visitado de uma visita sem aviso nunca é avisado (RN31).
 */
@Component
public class VisitAlertJob {

  private static final Logger log = LoggerFactory.getLogger(VisitAlertJob.class);

  /** Resultado atrasado: passaram estes dias da data marcada (doc 1 §6.3). */
  static final int RESULT_MISSING_AFTER_DAYS = 3;

  /** RN36: resumo recalculado para as visitas dos próximos dias. */
  static final int BRIEF_DAYS_AHEAD = 14;

  /** Maior antecedência de lembrete aceita na associação (visit_reminder_days). */
  private static final int MAX_REMINDER_DAYS = 30;

  private final AssociationRepository associations;
  private final VisitStatusService statuses;
  private final VisitRepository visits;
  private final VisitParticipantRepository participants;
  private final VisitService visitService;
  private final VisitNotifier notifier;
  private final PropertyRepository properties;
  private final UserRepository users;
  private final NotificationService notifications;
  private final MailService mail;
  private final MailComposer composer;
  private final AlertLog alertLog;
  private final CertificationReader clock;
  private final TransactionTemplate tx;

  public VisitAlertJob(
      AssociationRepository associations,
      VisitStatusService statuses,
      VisitRepository visits,
      VisitParticipantRepository participants,
      VisitService visitService,
      VisitNotifier notifier,
      PropertyRepository properties,
      UserRepository users,
      NotificationService notifications,
      MailService mail,
      MailComposer composer,
      AlertLog alertLog,
      CertificationReader clock,
      TransactionTemplate tx) {
    this.associations = associations;
    this.statuses = statuses;
    this.visits = visits;
    this.participants = participants;
    this.visitService = visitService;
    this.notifier = notifier;
    this.properties = properties;
    this.users = users;
    this.notifications = notifications;
    this.mail = mail;
    this.composer = composer;
    this.alertLog = alertLog;
    this.clock = clock;
    this.tx = tx;
  }

  @Scheduled(cron = "0 5 7 * * *", zone = "${app.timezone}")
  public void run() {
    LocalDate today = clock.today();
    for (var association : associations.findAll()) {
      safely("período de visitas", () -> windowOpenings(association));
    }
    safely("lembretes", () -> reminders(today));
    safely("resultados atrasados", () -> resultsMissing(today));
    safely(
        "resumos",
        () ->
            tx.executeWithoutResult(
                s -> visitService.refreshUpcomingBriefs(today, BRIEF_DAYS_AHEAD)));
  }

  /** Abertura do período: o agricultor recebe push; os coordenadores, um resumo por dia. */
  void windowOpenings(Association association) {
    tx.executeWithoutResult(s -> {
      var opened = new ArrayList<PropertyVisitStatus>();
      for (var st : statuses.all(association.getId())) {
        var certId = st.cycle().certificationId();
        if (st.windowOpen()
            && certId != null
            && alertLog.markIfNew(certId, "VISIT_WINDOW_OPEN:" + certId)) {
          opened.add(st);
          notifications.notify(
              List.of(st.property().farmerId()),
              NotificationType.VISIT_WINDOW_OPEN,
              "Época das visitas da certificação",
              "Está chegando a época das visitas da certificação do %s. A associação vai combinar"
                      .formatted(st.property().name())
                  + " as datas com o grupo.",
              Map.of("propertyId", st.property().id().toString()));
        }
      }
      if (opened.isEmpty()) {
        return;
      }
      String text =
          "Período de visitas aberto para %d propriedade(s): %s."
              .formatted(
                  opened.size(),
                  String.join(", ", opened.stream().map(o -> o.property().name()).toList()));
      var coordinators = notifier.coordinators(association.getId());
      notifications.notify(
          coordinators,
          NotificationType.VISIT_WINDOW_OPEN,
          "Período de visitas aberto",
          text,
          Map.of());
      users.findAllById(coordinators).stream()
          .map(u -> u.getEmail())
          .filter(Objects::nonNull)
          .forEach(email ->
              mail.sendAfterCommit(
                  composer.coordinatorNotice(
                      email, "Período de visitas aberto", text, "/visitas")));
    });
  }

  /** Véspera (ou N dias antes) da visita; a data entra na chave para a nova data também avisar. */
  void reminders(LocalDate today) {
    var reminderDays =
        associations.findAll().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    Association::getId, Association::getVisitReminderDays));
    for (var visit :
        visits.findByStatusAndPlannedOnBetweenAndDeletedAtIsNull(
            VisitStatus.PLANNED, today, today.plusDays(MAX_REMINDER_DAYS))) {
      int days = reminderDays.getOrDefault(visit.getAssociationId(), 0);
      if (days <= 0 || !visit.getPlannedOn().equals(today.plusDays(days))) {
        continue;
      }
      safely(
          "lembrete " + visit.getId(),
          () ->
              tx.executeWithoutResult(s -> {
                if (!alertLog.markIfNew(
                    visit.getId(),
                    "VISIT_REMINDER:" + visit.getId() + ":" + visit.getPlannedOn())) {
                  return;
                }
                var property = properties.findById(visit.getPropertyId()).orElseThrow();
                var ctx =
                    new VisitNotifier.Context(
                        visit, property.getName(), property.getFarmerId(), null);
                var farmers =
                    participants.findByVisitIdAndDeletedAtIsNull(visit.getId()).stream()
                        .map(VisitParticipant::getFarmerId)
                        .filter(Objects::nonNull)
                        .toList();
                String when =
                    days == 1
                        ? "Amanhã"
                        : "Em "
                            + BrFormat.days(days)
                            + " ("
                            + BrFormat.weekdayAndDay(visit.getPlannedOn())
                            + ")";
                notifications.notify(
                    VisitNotifier.recipients(ctx, farmers),
                    NotificationType.VISIT_REMINDER,
                    "Lembrete de visita",
                    "%s: visita ao %s.".formatted(when, property.getName()),
                    VisitNotifier.data(ctx));
              }));
    }
  }

  /** Três dias depois da data sem resultado: avisa os coordenadores. */
  void resultsMissing(LocalDate today) {
    for (var visit :
        visits.findByStatusAndPlannedOnBeforeAndDeletedAtIsNull(
            VisitStatus.PLANNED, today.minusDays(RESULT_MISSING_AFTER_DAYS - 1L))) {
      safely(
          "resultado " + visit.getId(),
          () ->
              tx.executeWithoutResult(s -> {
                if (!alertLog.markIfNew(visit.getId(), "VISIT_RESULT_MISSING:" + visit.getId())) {
                  return;
                }
                var property = properties.findById(visit.getPropertyId()).orElseThrow();
                var ctx =
                    new VisitNotifier.Context(
                        visit, property.getName(), property.getFarmerId(), null);
                notifications.notify(
                    notifier.coordinators(visit.getAssociationId()),
                    NotificationType.VISIT_RESULT_MISSING,
                    "Visita sem resultado",
                    "A visita ao %s de %s ainda não tem resultado."
                        .formatted(property.getName(), BrFormat.date(visit.getPlannedOn())),
                    VisitNotifier.data(ctx));
              }));
    }
  }

  /** Um aviso com problema não pode impedir os outros. */
  private static void safely(String what, Runnable step) {
    try {
      step.run();
    } catch (RuntimeException e) {
      log.error("Falha na rotina de avisos do SPG ({})", what, e);
    }
  }
}
