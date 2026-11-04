// Cenário comum dos testes do SPG: grupo, agricultores com localização, Comissão e atalhos de
// visita.
package br.edu.ifba.agrofacil.spg;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.ifba.agrofacil.IntegrationTest;
import br.edu.ifba.agrofacil.notification.NotificationRepository;
import br.edu.ifba.agrofacil.notification.NotificationType;
import br.edu.ifba.agrofacil.spg.checklist.ChecklistService;
import br.edu.ifba.agrofacil.spg.visit.VisitAlertJob;
import br.edu.ifba.agrofacil.support.Fixtures;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;

abstract class SpgScenarioTest extends IntegrationTest {

  @Autowired protected NotificationRepository notifications;
  @Autowired protected ChecklistService checklist;
  @Autowired protected VisitAlertJob alertJob;

  protected Fixtures.Coordinator coordinator;
  protected Fixtures.Farmer visited;
  protected Fixtures.Farmer near;
  protected Fixtures.Farmer far;
  protected Fixtures.Farmer committee;
  protected String propertyId;
  protected String groupId;

  @BeforeEach
  void scenario() throws Exception {
    coordinator = fixtures.coordinator();
    groupId = group("Lagoa Verde");
    String otherGroup = group("Lagoa Seca");
    visited = farmerInGroup("José Visitado", groupId);
    near = farmerInGroup("Ana Perto", groupId);
    far = farmerInGroup("Bruno Longe", groupId);
    committee = farmerInGroup("Carla Comissão", otherGroup);
    propertyId = propertyAt(visited, "Sítio Boa Vista", -11.3045, -41.8553);
    propertyAt(near, "Sítio Vizinho", -11.31, -41.86);
    propertyAt(far, "Sítio Distante", -11.60, -42.10);
    call(
            HttpMethod.POST,
            "/committee-members",
            coordinator.accessToken(),
            """
            {"person": {"kind": "FARMER", "id": "%s"}, "representedGroupId": "%s",
             "role": "HOLDER", "startedOn": "%s"}\
            """
                .formatted(committee.user().getId(), otherGroup, today().minusDays(30)))
        .andExpect(status().isCreated());
  }

  protected LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of("America/Bahia")));
  }

  protected String group(String name) throws Exception {
    return read(
        call(
                HttpMethod.POST,
                "/groups",
                coordinator.accessToken(),
                """
                {"name": "%s %s"}\
                """
                    .formatted(name, System.nanoTime()))
            .andExpect(status().isCreated()),
        "$.id");
  }

  protected Fixtures.Farmer farmerInGroup(String name, String group) throws Exception {
    var farmer = fixtures.farmer(name);
    Integer version =
        read(
            call(
                HttpMethod.GET,
                "/farmers/" + farmer.user().getId(),
                coordinator.accessToken(),
                null),
            "$.version");
    call(
            HttpMethod.PATCH,
            "/farmers/" + farmer.user().getId(),
            coordinator.accessToken(),
            """
            {"groupId": "%s", "version": %d}\
            """
                .formatted(group, version))
        .andExpect(status().isOk());
    return farmer;
  }

  protected String propertyAt(Fixtures.Farmer owner, String name, double lat, double lng)
      throws Exception {
    var id = fixtures.property(coordinator, owner, name).id().toString();
    call(
            HttpMethod.PATCH,
            "/properties/" + id,
            coordinator.accessToken(),
            """
            {"location": {"lat": %s, "lng": %s}, "version": 0}\
            """
                .formatted(lat, lng))
        .andExpect(status().isOk());
    return id;
  }

  protected String createVisit(String type, boolean unannounced, Fixtures.Farmer visitor)
      throws Exception {
    return read(
        call(
                HttpMethod.POST,
                "/visits",
                coordinator.accessToken(),
                """
                {"propertyId": "%s", "type": "%s", "unannounced": %s, "plannedOn": "%s",
                 "participants": [{"person": {"kind": "FARMER", "id": "%s"}, "role": "VISITOR"}],
                 "recorderId": "%s"}\
                """
                    .formatted(
                        propertyId,
                        type,
                        unannounced,
                        today().plusDays(5),
                        visitor.user().getId(),
                        visitor.user().getId()))
            .andExpect(status().isCreated()),
        "$.id");
  }

  protected long notificationsOf(Fixtures.Farmer f, NotificationType type) {
    return notifications.findByUserId(f.user().getId(), Pageable.unpaged()).stream()
        .filter(n -> n.getType() == type)
        .count();
  }

  protected List<String> pulledVisitIds(Fixtures.Farmer f) throws Exception {
    return read(
        call(HttpMethod.GET, "/sync/pull?cursor=0&limit=500", f.accessToken(), null)
            .andExpect(status().isOk()),
        "$.changes.visits[*].id");
  }

  protected String answersJson() {
    return checklist.effective(Fixtures.ASSOCIATION, "VERIFICATION", false).stream()
        .map(i -> "{\"itemCode\": \"%s\", \"answer\": \"YES\"}".formatted(i.getCode()))
        .collect(Collectors.joining(","));
  }

  protected String pushResult(
      Fixtures.Farmer who, String visitId, String participantId, String answers) throws Exception {
    var result =
        call(
                HttpMethod.POST,
                "/sync/push",
                who.accessToken(),
                """
                {"deviceId": "%s", "mutations": [{"mutationId": "%s", "entity": "visit", "op": "UPSERT",
                 "entityId": "%s", "clientTimestamp": "2026-11-04T10:00:00Z",
                 "data": {"doneOn": "%s", "attendedParticipantIds": ["%s"], "answers": [%s],
                          "issuesFound": false}}]}\
                """
                    .formatted(
                        who.deviceId(),
                        UUID.randomUUID(),
                        visitId,
                        today(),
                        participantId,
                        answers))
            .andExpect(status().isOk());
    return read(result, "$.results[0].status");
  }
}
