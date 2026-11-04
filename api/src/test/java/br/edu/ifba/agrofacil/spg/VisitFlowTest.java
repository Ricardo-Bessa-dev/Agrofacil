// Visitas do SPG de ponta a ponta: sugestão, regras de quem visita, visita sem aviso, resultado
// pelo app e pelo painel, pull e escopo (RN29–RN36).
package br.edu.ifba.agrofacil.spg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.ifba.agrofacil.notification.NotificationType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class VisitFlowTest extends SpgScenarioTest {

  @Test
  void suggestion_ordersByDistance_andExcludesConflicts_RN33_RN40() throws Exception {
    call(
            HttpMethod.POST,
            "/member-conflicts",
            coordinator.accessToken(),
            """
            {"farmerAId": "%s", "farmerBId": "%s", "reason": "FAMILY"}\
            """
                .formatted(visited.user().getId(), far.user().getId()))
        .andExpect(status().isCreated());
    var s =
        call(
                HttpMethod.POST,
                "/visits/suggest-visitors",
                coordinator.accessToken(),
                """
                {"propertyId": "%s", "type": "PEER", "plannedOn": "%s"}\
                """
                    .formatted(propertyId, today().plusDays(5)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eligible[0].person.name").value("Ana Perto"))
            .andExpect(jsonPath("$.eligible[0].preselected").value(true))
            .andExpect(jsonPath("$.eligible[0].distanceEstimated").value(false));
    List<String> excluded = read(s, "$.excluded[*].reason");
    assertThat(excluded).contains("impedimento cadastrado com José Visitado");

    // A gravação usa a mesma regra da sugestão.
    call(
            HttpMethod.POST,
            "/visits",
            coordinator.accessToken(),
            """
            {"propertyId": "%s", "type": "PEER", "unannounced": false, "plannedOn": "%s",
             "participants": [{"person": {"kind": "FARMER", "id": "%s"}, "role": "VISITOR"}]}\
            """
                .formatted(propertyId, today().plusDays(5), far.user().getId()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VISITOR_NOT_ELIGIBLE"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value(org.hamcrest.Matchers.containsString("Bruno Longe")));
  }

  @Test
  void unannouncedPeerVisit_isRejected_RN31() throws Exception {
    call(
            HttpMethod.POST,
            "/visits",
            coordinator.accessToken(),
            """
            {"propertyId": "%s", "type": "PEER", "unannounced": true, "plannedOn": "%s",
             "participants": [{"person": {"kind": "FARMER", "id": "%s"}, "role": "VISITOR"}]}\
            """
                .formatted(propertyId, today().plusDays(5), near.user().getId()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("UNANNOUNCED_ONLY_VERIFICATION"));
  }

  @Test
  void unannouncedVisit_isHiddenFromTheVisitedUntilDone_andTheRecorderRegistersByApp_RN31_RN35()
      throws Exception {
    var visitId = createVisit("VERIFICATION", true, committee);
    assertThat(notificationsOf(visited, NotificationType.VISIT_SCHEDULED)).isZero();
    assertThat(notificationsOf(committee, NotificationType.VISIT_ASSIGNED)).isEqualTo(1);
    assertThat(pulledVisitIds(visited)).doesNotContain(visitId);
    var pull = call(HttpMethod.GET, "/sync/pull?cursor=0&limit=500", committee.accessToken(), null);
    String participantId =
        read(pull, "$.changes.visits[?(@.id == '" + visitId + "')].participants[0].id")
            .toString()
            .replaceAll("[\\[\\]\"]", "");
    List<String> briefFarmer =
        read(pull, "$.changes.visits[?(@.id == '" + visitId + "')].brief.farmer.name");
    assertThat(briefFarmer).containsExactly("José Visitado"); // o visitador recebe o resumo

    // Outro agricultor não pode registrar; faltando perguntas, o roteiro é recusado.
    assertThat(pushResult(near, visitId, participantId, answersJson())).isEqualTo("REJECTED");
    assertThat(pushResult(committee, visitId, participantId, "")).isEqualTo("REJECTED");
    assertThat(pushResult(committee, visitId, participantId, answersJson())).isEqualTo("APPLIED");

    call(HttpMethod.GET, "/visits/" + visitId, coordinator.accessToken(), null)
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.result.source").value("MOBILE"));
    assertThat(notificationsOf(visited, NotificationType.VISIT_SCHEDULED)).isZero();
    assertThat(pulledVisitIds(visited)).contains(visitId); // concluída: o visitado passa a ver
    List<Object> ownerBrief =
        read(
            call(HttpMethod.GET, "/sync/pull?cursor=0&limit=500", visited.accessToken(), null),
            "$.changes.visits[?(@.id == '" + visitId + "')].brief");
    assertThat(ownerBrief).containsOnlyNulls(); // o visitado não recebe o resumo (RN36)

    call(
            HttpMethod.GET,
            "/properties/" + propertyId + "/visit-status",
            coordinator.accessToken(),
            null)
        .andExpect(jsonPath("$.verification.done").value(1));
  }

  @Test
  void announcedVisit_notifiesTheVisited_andCancelSendsTombstoneToTheVisitor_RN30_RN36()
      throws Exception {
    var visitId = createVisit("PEER", false, near);
    assertThat(notificationsOf(visited, NotificationType.VISIT_SCHEDULED)).isEqualTo(1);
    assertThat(pulledVisitIds(near)).contains(visitId);
    call(
            HttpMethod.POST,
            "/visits/" + visitId + "/cancel",
            coordinator.accessToken(),
            """
            {"reason": "Chuva forte"}\
            """)
        .andExpect(status().isNoContent());
    assertThat(notificationsOf(near, NotificationType.VISIT_CANCELLED)).isEqualTo(1);
    List<String> tombstones =
        read(
            call(HttpMethod.GET, "/sync/pull?cursor=0&limit=500", near.accessToken(), null),
            "$.deleted[?(@.entity == 'visit')].id");
    assertThat(tombstones).contains(visitId);
    call(
            HttpMethod.POST,
            "/visits/" + visitId + "/cancel",
            coordinator.accessToken(),
            """
            {"reason": "de novo"}\
            """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VISIT_NOT_EDITABLE"));
  }

  @Test
  void panelResult_requiresEveryChecklistAnswer_RN35() throws Exception {
    var visitId = createVisit("PEER", false, near);
    var detail = call(HttpMethod.GET, "/visits/" + visitId, coordinator.accessToken(), null);
    String participantId = read(detail, "$.participants[0].id");
    Integer version = read(detail, "$.version");
    call(
            HttpMethod.PUT,
            "/visits/" + visitId + "/result",
            coordinator.accessToken(),
            """
            {"doneOn": "%s", "attendedParticipantIds": ["%s"], "answers": [],
             "issuesFound": false, "version": %d}\
            """
                .formatted(today(), participantId, version))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("CHECKLIST_INCOMPLETE"));
  }

  @Test
  void reminderJob_sendsTheEveOnce_RN30() throws Exception {
    var visitId =
        read(
            call(
                    HttpMethod.POST,
                    "/visits",
                    coordinator.accessToken(),
                    """
                    {"propertyId": "%s", "type": "PEER", "unannounced": false, "plannedOn": "%s",
                     "participants": [{"person": {"kind": "FARMER", "id": "%s"}, "role": "VISITOR"}]}\
                    """
                        .formatted(propertyId, today().plusDays(1), near.user().getId()))
                .andExpect(status().isCreated()),
            "$.id");
    alertJob.run();
    alertJob.run();
    assertThat(notificationsOf(near, NotificationType.VISIT_REMINDER)).isEqualTo(1);
    assertThat(notificationsOf(visited, NotificationType.VISIT_REMINDER)).isEqualTo(1);
    assertThat(visitId).isNotNull();
  }

  @Test
  void farmer_cannotUseSpgRoutes() throws Exception {
    for (var path :
        List.of(
            "/visits",
            "/visits/status",
            "/collaborators",
            "/committee-members",
            "/member-conflicts")) {
      call(HttpMethod.GET, path, near.accessToken(), null).andExpect(status().isForbidden());
    }
  }
}
