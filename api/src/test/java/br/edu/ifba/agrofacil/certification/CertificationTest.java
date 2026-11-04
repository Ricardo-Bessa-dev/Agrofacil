// Certificações pelo painel e rotina de alertas: cadastro, renovação, status e avisos (RN18–RN20).
package br.edu.ifba.agrofacil.certification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.ifba.agrofacil.IntegrationTest;
import br.edu.ifba.agrofacil.notification.Notification;
import br.edu.ifba.agrofacil.notification.NotificationRepository;
import br.edu.ifba.agrofacil.notification.NotificationType;
import br.edu.ifba.agrofacil.support.Fixtures;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;

class CertificationTest extends IntegrationTest {

  @Autowired private CertificationAlertJob alertJob;
  @Autowired private NotificationRepository notifications;

  private Fixtures.Coordinator coordinator;
  private Fixtures.Farmer farmer;
  private String propertyId;
  private String workerId;

  @BeforeEach
  void scenario() throws Exception {
    coordinator = fixtures.coordinator();
    farmer = fixtures.farmer("José do Licuri");
    propertyId = fixtures.property(coordinator, farmer, "Sítio Licuri").id().toString();
    var worker =
        call(
                HttpMethod.POST,
                "/properties/" + propertyId + "/workers",
                coordinator.accessToken(),
                """
                {"name": "Ana do Licuri", "relationship": "FAMILY"}\
                """)
            .andExpect(status().isCreated());
    workerId = read(worker, "$.id");
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of("America/Bahia")));
  }

  private String create(LocalDate issuedOn, LocalDate expiresOn) throws Exception {
    var result =
        call(
                HttpMethod.POST,
                "/certifications",
                coordinator.accessToken(),
                """
                {"propertyId": "%s", "number": "RPM-001", "issuedOn": "%s"%s,
                 "products": [{"productName": "Umbu"}], "workerIds": ["%s"]}\
                """
                    .formatted(
                        propertyId,
                        issuedOn,
                        expiresOn == null ? "" : ", \"expiresOn\": \"" + expiresOn + "\"",
                        workerId))
            .andExpect(status().isCreated());
    return read(result, "$.id");
  }

  private List<Notification> notificationsOf(UUID userId, NotificationType type) {
    return notifications.findByUserId(userId, Pageable.unpaged()).stream()
        .filter(n -> n.getType() == type)
        .toList();
  }

  @Test
  void create_withoutExpiry_validForOneYear_andIsCurrent_RN18() throws Exception {
    var issued = today().minusDays(10);
    var id = create(issued, null);
    call(HttpMethod.GET, "/certifications/" + id, coordinator.accessToken(), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiresOn").value(issued.plusYears(1).toString()))
        .andExpect(jsonPath("$.status").value("VALID"))
        .andExpect(jsonPath("$.isCurrent").value(true))
        .andExpect(jsonPath("$.workerIds[0]").value(workerId))
        .andExpect(jsonPath("$.property.farmerName").value("José do Licuri"));
  }

  @Test
  void expiryBeforeIssue_productsMissing_andForeignWorker_areRejected_RN18() throws Exception {
    var other = fixtures.property(coordinator, farmer, "Outra roça").id();
    var otherWorker =
        read(
            call(
                    HttpMethod.POST,
                    "/properties/" + other + "/workers",
                    coordinator.accessToken(),
                    """
                    {"name": "Pedro", "relationship": "EMPLOYEE"}\
                    """)
                .andExpect(status().isCreated()),
            "$.id");
    var result =
        call(
                HttpMethod.POST,
                "/certifications",
                coordinator.accessToken(),
                """
                {"propertyId": "%s", "issuedOn": "%s", "expiresOn": "%s",
                 "products": [], "workerIds": ["%s"]}\
                """
                    .formatted(propertyId, today(), today(), otherWorker))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    List<String> fields = read(result, "$.errors[*].field");
    assertThat(fields).containsExactlyInAnyOrder("expiresOn", "products", "workerIds[0]");
  }

  @Test
  void renew_keepsTheOldOneValid_copiesProductsAndWorkers_andMovesIsCurrent_RN19()
      throws Exception {
    var oldId = create(today().minusMonths(11), null);
    var renewed =
        call(
                HttpMethod.POST,
                "/certifications/" + oldId + "/renew",
                coordinator.accessToken(),
                """
                {"issuedOn": "%s"}\
                """
                    .formatted(today()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.previousId").value(oldId))
            .andExpect(jsonPath("$.products[0].productName").value("Umbu"))
            .andExpect(jsonPath("$.workerIds[0]").value(workerId))
            .andExpect(jsonPath("$.isCurrent").value(true));
    String newId = read(renewed, "$.id");

    call(
            HttpMethod.GET,
            "/properties/" + propertyId + "/certifications",
            coordinator.accessToken(),
            null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(newId))
        .andExpect(jsonPath("$[1].id").value(oldId))
        .andExpect(jsonPath("$[1].isCurrent").value(false))
        .andExpect(jsonPath("$[1].status").value("EXPIRING")); // a anterior vale até vencer

    // O app recebe as duas, cada uma com o isCurrent atualizado.
    var pull =
        call(HttpMethod.GET, "/sync/pull?cursor=0&limit=500", farmer.accessToken(), null)
            .andExpect(status().isOk());
    List<Boolean> current =
        read(pull, "$.changes.certifications[?(@.id == '" + oldId + "')].isCurrent");
    assertThat(current).containsExactly(false);
  }

  @Test
  void suspend_requiresReason_notifiesFarmer_andOnlySuspendedCanBeReactivated_RN19()
      throws Exception {
    var id = create(today(), null);
    call(
            HttpMethod.POST,
            "/certifications/" + id + "/suspend",
            coordinator.accessToken(),
            """
            {"reason": " "}\
            """)
        .andExpect(status().isUnprocessableContent());
    call(HttpMethod.POST, "/certifications/" + id + "/reactivate", coordinator.accessToken(), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    call(
            HttpMethod.POST,
            "/certifications/" + id + "/suspend",
            coordinator.accessToken(),
            """
            {"reason": "Uso de adubo não permitido"}\
            """)
        .andExpect(status().isNoContent());
    call(HttpMethod.GET, "/certifications/" + id, coordinator.accessToken(), null)
        .andExpect(jsonPath("$.status").value("SUSPENDED"))
        .andExpect(jsonPath("$.statusReason").value("Uso de adubo não permitido"));
    assertThat(notificationsOf(farmer.user().getId(), NotificationType.CERTIFICATION_SUSPENDED))
        .hasSize(1);

    call(
            HttpMethod.POST,
            "/certifications/" + id + "/cancel",
            coordinator.accessToken(),
            """
            {"reason": "Desligamento"}\
            """)
        .andExpect(status().isNoContent());
    call(HttpMethod.POST, "/certifications/" + id + "/reactivate", coordinator.accessToken(), null)
        .andExpect(status().isConflict());
  }

  @Test
  void propertyWithCertificationInForce_cannotBeDeleted_RN07() throws Exception {
    create(today(), null);
    call(HttpMethod.DELETE, "/properties/" + propertyId, coordinator.accessToken(), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROPERTY_HAS_ACTIVE_CERTIFICATION"));
  }

  @Test
  void farmer_cannotUseCertificationRoutes() throws Exception {
    call(HttpMethod.GET, "/certifications", farmer.accessToken(), null)
        .andExpect(status().isForbidden());
  }

  @Test
  void listFilters_expiringWithinDays_andFarmer() throws Exception {
    create(today().minusYears(1).plusDays(20), null); // vence em 20 dias
    call(
            HttpMethod.GET,
            "/certifications?expiringWithinDays=30&farmerId=" + farmer.user().getId(),
            coordinator.accessToken(),
            null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].daysToExpire").value(20));
    call(
            HttpMethod.GET,
            "/certifications?expiringWithinDays=10&farmerId=" + farmer.user().getId(),
            coordinator.accessToken(),
            null)
        .andExpect(jsonPath("$.page.totalElements").value(0));
  }

  @Test
  void alertJob_sendsEachThresholdOnce_thenTheExpiredAlert_RN20() throws Exception {
    var id = create(today().minusYears(1).plusDays(30), null); // vence em 30 dias
    var farmerId = farmer.user().getId();

    alertJob.run();
    alertJob.run(); // rodar de novo no mesmo dia não repete
    var expiring = notificationsOf(farmerId, NotificationType.CERTIFICATION_EXPIRING);
    assertThat(expiring).hasSize(1);
    assertThat(expiring.getFirst().getTitle()).isEqualTo("Sua certificação vence em 30 dias");
    assertThat(notificationsOf(coordinator.user().getId(), NotificationType.CERTIFICATION_EXPIRING))
        .isNotEmpty();
    assertThat(mail.allTo(coordinator.user().getEmail()))
        .anyMatch(m -> m.body().contains("/certificacoes/" + id));

    clock.advance(Duration.ofDays(16)); // faltam 14: passou do limite de 15
    alertJob.run();
    assertThat(notificationsOf(farmerId, NotificationType.CERTIFICATION_EXPIRING)).hasSize(2);

    clock.advance(Duration.ofDays(15)); // venceu ontem
    alertJob.run();
    alertJob.run();
    assertThat(notificationsOf(farmerId, NotificationType.CERTIFICATION_EXPIRED)).hasSize(1);
  }

  @Test
  void alertJob_ignoresSuspendedAndReplacedCertifications_RN20() throws Exception {
    var old = create(today().minusYears(1).plusDays(30), null);
    call(
            HttpMethod.POST,
            "/certifications/" + old + "/renew",
            coordinator.accessToken(),
            """
            {"issuedOn": "%s"}\
            """
                .formatted(today()))
        .andExpect(status().isCreated());
    alertJob.run();
    assertThat(notificationsOf(farmer.user().getId(), NotificationType.CERTIFICATION_EXPIRING))
        .isEmpty();
  }
}
