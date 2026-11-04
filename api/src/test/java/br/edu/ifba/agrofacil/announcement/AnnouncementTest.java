// Comunicados: público (todos ou grupo), notificação, pull, edição sem novo push e exclusão (RN23).
package br.edu.ifba.agrofacil.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.ifba.agrofacil.IntegrationTest;
import br.edu.ifba.agrofacil.notification.NotificationRepository;
import br.edu.ifba.agrofacil.notification.NotificationType;
import br.edu.ifba.agrofacil.support.Fixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;

class AnnouncementTest extends IntegrationTest {

  @Autowired private NotificationRepository notifications;

  private Fixtures.Coordinator coordinator;
  private Fixtures.Farmer inGroup;
  private Fixtures.Farmer outside;
  private String groupId;

  @BeforeEach
  void scenario() throws Exception {
    coordinator = fixtures.coordinator();
    inGroup = fixtures.farmer("Rita do Grupo");
    outside = fixtures.farmer("Caio sem Grupo");
    groupId =
        read(
            call(
                    HttpMethod.POST,
                    "/groups",
                    coordinator.accessToken(),
                    """
                    {"name": "Grupo %s"}\
                    """
                        .formatted(System.nanoTime()))
                .andExpect(status().isCreated()),
            "$.id");
    var farmer =
        call(HttpMethod.GET, "/farmers/" + inGroup.user().getId(), coordinator.accessToken(), null)
            .andExpect(status().isOk());
    Integer version = read(farmer, "$.version");
    call(
            HttpMethod.PATCH,
            "/farmers/" + inGroup.user().getId(),
            coordinator.accessToken(),
            """
            {"groupId": "%s", "version": %d}\
            """
                .formatted(groupId, version))
        .andExpect(status().isOk());
  }

  private String publish(String group) throws Exception {
    var result =
        call(
                HttpMethod.POST,
                "/announcements",
                coordinator.accessToken(),
                """
                {"type": "NOTICE", "title": "Reunião sábado", "body": "Reunião às 8h na sede."%s}\
                """
                    .formatted(group == null ? "" : ", \"groupId\": \"" + group + "\""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.publishedAt").exists());
    return read(result, "$.id");
  }

  private long announcementNotifications(Fixtures.Farmer farmer) {
    return notifications.findByUserId(farmer.user().getId(), Pageable.unpaged()).stream()
        .filter(n -> n.getType() == NotificationType.ANNOUNCEMENT)
        .count();
  }

  private List<String> pulledAnnouncementIds(Fixtures.Farmer farmer) throws Exception {
    return read(
        call(HttpMethod.GET, "/sync/pull?cursor=0&limit=500", farmer.accessToken(), null)
            .andExpect(status().isOk()),
        "$.changes.announcements[*].id");
  }

  @Test
  void announcementForEveryone_reachesAllFarmers_RN23() throws Exception {
    var id = publish(null);
    assertThat(announcementNotifications(inGroup)).isEqualTo(1);
    assertThat(announcementNotifications(outside)).isEqualTo(1);
    assertThat(pulledAnnouncementIds(outside)).contains(id);
  }

  @Test
  void announcementForAGroup_onlyReachesItsFarmers_RN23() throws Exception {
    var id = publish(groupId);
    assertThat(announcementNotifications(inGroup)).isEqualTo(1);
    assertThat(announcementNotifications(outside)).isZero();
    assertThat(pulledAnnouncementIds(inGroup)).contains(id);
    assertThat(pulledAnnouncementIds(outside)).doesNotContain(id);
  }

  @Test
  void editing_doesNotNotifyAgain_andTheAudienceIsFixed() throws Exception {
    var id = publish(groupId);
    call(
            HttpMethod.PATCH,
            "/announcements/" + id,
            coordinator.accessToken(),
            """
            {"title": "Reunião domingo", "version": 0}\
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Reunião domingo"));
    assertThat(announcementNotifications(inGroup)).isEqualTo(1);
    call(
            HttpMethod.PATCH,
            "/announcements/" + id,
            coordinator.accessToken(),
            """
            {"groupId": null, "version": 1}\
            """)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("groupId"));
  }

  @Test
  void deleted_becomesATombstoneInThePull() throws Exception {
    var id = publish(null);
    call(HttpMethod.DELETE, "/announcements/" + id, coordinator.accessToken(), null)
        .andExpect(status().isNoContent());
    var pull =
        call(HttpMethod.GET, "/sync/pull?cursor=0&limit=500", outside.accessToken(), null)
            .andExpect(status().isOk());
    List<String> tombstones = read(pull, "$.deleted[?(@.entity == 'announcement')].id");
    assertThat(tombstones).contains(id);
    call(HttpMethod.GET, "/announcements", coordinator.accessToken(), null)
        .andExpect(jsonPath("$.content[?(@.id == '" + id + "')]").isEmpty());
  }

  @Test
  void farmer_cannotUseAnnouncementRoutes() throws Exception {
    call(HttpMethod.GET, "/announcements", outside.accessToken(), null)
        .andExpect(status().isForbidden());
  }
}
