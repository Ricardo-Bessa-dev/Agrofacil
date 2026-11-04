// Envia o alerta de vencimento de uma certificação ao agricultor e aos coordenadores (RN20).
package br.edu.ifba.agrofacil.certification;

import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.common.domain.BrFormat;
import br.edu.ifba.agrofacil.common.security.Role;
import br.edu.ifba.agrofacil.mail.MailComposer;
import br.edu.ifba.agrofacil.mail.MailService;
import br.edu.ifba.agrofacil.notification.NotificationService;
import br.edu.ifba.agrofacil.notification.NotificationType;
import br.edu.ifba.agrofacil.property.Property;
import br.edu.ifba.agrofacil.property.PropertyRepository;
import br.edu.ifba.agrofacil.spg.visit.VisitStatusService;
import br.edu.ifba.agrofacil.user.AppUser;
import br.edu.ifba.agrofacil.user.UserRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Destinatários (RN20): o agricultor recebe notificação + push (e e-mail, se tiver); os
 * coordenadores ativos recebem notificação + e-mail.
 */
@Service
public class CertificationAlertService {

  private final CertificationRepository certifications;
  private final CertificationAlertLog alertLog;
  private final PropertyRepository properties;
  private final UserRepository users;
  private final AssociationService associations;
  private final NotificationService notifications;
  private final MailService mail;
  private final MailComposer composer;
  private final VisitStatusService visitStatus;

  public CertificationAlertService(
      CertificationRepository certifications,
      CertificationAlertLog alertLog,
      PropertyRepository properties,
      UserRepository users,
      AssociationService associations,
      NotificationService notifications,
      MailService mail,
      MailComposer composer,
      VisitStatusService visitStatus) {
    this.certifications = certifications;
    this.alertLog = alertLog;
    this.properties = properties;
    this.users = users;
    this.associations = associations;
    this.notifications = notifications;
    this.mail = mail;
    this.composer = composer;
    this.visitStatus = visitStatus;
  }

  /**
   * Envia o alerta do dia, se houver, numa transação própria: a falha de uma certificação não
   * impede as outras.
   *
   * @return true se um alerta foi enviado
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean process(UUID certificationId, LocalDate today) {
    var cert = certifications.findByIdAndDeletedAtIsNull(certificationId).orElse(null);
    if (cert == null || cert.getManualStatus() != ManualStatus.ACTIVE) {
      return false;
    }
    var property = properties.findById(cert.getPropertyId()).orElseThrow();
    var association = associations.get(property.getAssociationId());
    var plan =
        AlertPlanner.nextAlert(
            cert.getExpiresOn(),
            today,
            association.getAlertDays(),
            alertLog.sentDays(cert.getId()));
    if (plan.isEmpty()) {
      return false;
    }
    send(cert, property, ChronoUnit.DAYS.between(today, cert.getExpiresOn()));
    alertLog.markSent(cert.getId(), plan.get().markAsSent());
    return true;
  }

  private void send(Certification cert, Property property, long daysToExpire) {
    boolean expired = daysToExpire < 0;
    var type =
        expired ? NotificationType.CERTIFICATION_EXPIRED : NotificationType.CERTIFICATION_EXPIRING;
    var data =
        Map.of(
            "certificationId", cert.getId().toString(), "propertyId", property.getId().toString());
    String when = BrFormat.date(cert.getExpiresOn());
    var farmer = users.findById(property.getFarmerId()).orElseThrow();

    String farmerTitle =
        expired
            ? "Sua certificação venceu"
            : daysToExpire == 0
                ? "Sua certificação vence hoje"
                : "Sua certificação vence em " + BrFormat.days(daysToExpire);
    // Doc 1 §6.3: o alerta diz o que ainda falta no ciclo de visitas.
    String missing =
        VisitStatusService.missingText(
            visitStatus.statusOf(property.getAssociationId(), property.getId()));
    String suffix = missing.isEmpty() ? "" : " " + missing;
    String farmerBody =
        "%s: %s em %s. Fale com a associação para renovar."
                .formatted(property.getName(), expired ? "venceu" : "vence", when)
            + suffix;
    notifications.notify(List.of(farmer.getId()), type, farmerTitle, farmerBody, data);
    if (farmer.getEmail() != null && farmer.isActive()) {
      mail.sendAfterCommit(composer.farmerNotice(farmer.getEmail(), farmerTitle, farmerBody));
    }

    var coordinators =
        users
            .findByAssociationIdAndRoleAndDeletedAtIsNullOrderByName(
                property.getAssociationId(), Role.COORDINATOR)
            .stream()
            .filter(AppUser::isActive)
            .toList();
    String coordTitle =
        expired
            ? "Certificação de %s venceu".formatted(farmer.getName())
            : "Certificação de %s vence em %s"
                .formatted(farmer.getName(), BrFormat.days(daysToExpire));
    String coordBody =
        "%s (%s): %s em %s."
                .formatted(property.getName(), farmer.getName(), expired ? "venceu" : "vence", when)
            + suffix;
    notifications.notify(
        coordinators.stream().map(AppUser::getId).toList(), type, coordTitle, coordBody, data);
    for (var c : coordinators) {
      if (c.getEmail() != null) {
        mail.sendAfterCommit(
            composer.coordinatorNotice(
                c.getEmail(), coordTitle, coordBody, "/certificacoes/" + cert.getId()));
      }
    }
  }
}
