// Rotina diária que avisa agricultor e coordenadores sobre certificações perto de vencer ou
// vencidas (RN20).
package br.edu.ifba.agrofacil.certification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Só a certificação atual e ativa de cada propriedade recebe alerta (doc 2 §1.12.1). */
@Component
public class CertificationAlertJob {

  private static final Logger log = LoggerFactory.getLogger(CertificationAlertJob.class);

  private final CertificationRepository certifications;
  private final CertificationReader reader;
  private final CertificationAlertService alerts;

  public CertificationAlertJob(
      CertificationRepository certifications,
      CertificationReader reader,
      CertificationAlertService alerts) {
    this.certifications = certifications;
    this.reader = reader;
    this.alerts = alerts;
  }

  /**
   * Roda às 07:00 no fuso da associação.
   *
   * @return quantos alertas foram enviados (útil nos testes e no log)
   */
  @Scheduled(cron = "0 0 7 * * *", zone = "${app.timezone}")
  public int run() {
    var today = reader.today();
    int sent = 0;
    var current = CertificationReader.pickCurrent(certifications.findAlertCandidates()).values();
    for (var cert : current) {
      if (cert.getManualStatus() != ManualStatus.ACTIVE) {
        continue;
      }
      try {
        if (alerts.process(cert.getId(), today)) {
          sent++;
        }
      } catch (RuntimeException e) {
        // Uma certificação com problema não pode impedir os alertas das outras.
        log.error("Falha no alerta da certificação {}", cert.getId(), e);
      }
    }
    log.info("Alertas de certificação: {} enviados", sent);
    return sent;
  }
}
