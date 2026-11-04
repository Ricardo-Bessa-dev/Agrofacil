// Casos obrigatórios de sincronização do lado do servidor (doc 1 §4.9).
package br.edu.ifba.agrofacil.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.ifba.agrofacil.IntegrationTest;
import br.edu.ifba.agrofacil.support.Fixtures;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class SyncTest extends IntegrationTest {

  private Fixtures.Coordinator coordinator;
  private Fixtures.Farmer farmer;
  private UUID propertyId;
  private UUID plotId;
  private final LocalDate today = LocalDate.now();

  @BeforeEach
  void scenario() throws Exception {
    coordinator = fixtures.coordinator();
    farmer = fixtures.farmer("João do Feijão");
    var property = fixtures.property(coordinator, farmer, "Roça do Alto");
    propertyId = property.id();
    var plots =
        call(
            HttpMethod.GET,
            "/properties/" + propertyId + "/plots",
            coordinator.accessToken(),
            null);
    plotId = UUID.fromString(read(plots, "$[0].id"));
  }

  private ResultActions push(Fixtures.Farmer who, String... mutations) throws Exception {
    return call(
        HttpMethod.POST,
        "/sync/push",
        who.accessToken(),
        """
        {"deviceId": "%s", "mutations": [%s]}\
        """
            .formatted(who.deviceId(), String.join(",", mutations)));
  }

  private static String mutation(UUID mutationId, String entity, String op, UUID id, String data) {
    return """
    {"mutationId": "%s", "entity": "%s", "op": "%s", "entityId": "%s",
     "clientTimestamp": "2026-11-04T10:00:00Z"%s}\
    """
        .formatted(mutationId, entity, op, id, data == null ? "" : ", \"data\": " + data);
  }

  private String plantingData(UUID id, UUID plot, LocalDate on) {
    return """
    {"id": "%s", "type": "PLANTING", "propertyId": "%s", "plotId": "%s", "performedOn": "%s",
     "cropOtherName": "Feijão de corda", "location": {"lat": -11.304512, "lng": -41.855301}}\
    """
        .formatted(id, propertyId, plot, on);
  }

  private String harvestData(UUID id, UUID plantingId, LocalDate on) {
    return """
    {"id": "%s", "type": "HARVEST", "propertyId": "%s", "plantingId": "%s", "performedOn": "%s",
     "quantity": {"value": 30, "unit": "BAG"}, "isFinal": false}\
    """
        .formatted(id, propertyId, plantingId, on);
  }

  private ResultActions pull(Fixtures.Farmer who, String cursor, int limit) throws Exception {
    return call(
            HttpMethod.GET,
            "/sync/pull?cursor=" + cursor + "&limit=" + limit,
            who.accessToken(),
            null)
        .andExpect(status().isOk());
  }

  @Test
  void case1and2_threeRecordsArriveInOrder_andRepeatingThePushDoesNotDuplicate() throws Exception {
    UUID planting = UUID.randomUUID();
    UUID harvest = UUID.randomUUID();
    UUID purchase = UUID.randomUUID();
    String[] batch = {
      mutation(
          UUID.randomUUID(),
          "record",
          "UPSERT",
          planting,
          plantingData(planting, plotId, today.minusDays(20))),
      mutation(
          UUID.randomUUID(),
          "record",
          "UPSERT",
          harvest,
          harvestData(harvest, planting, today.minusDays(1))),
      mutation(
          UUID.randomUUID(),
          "record",
          "UPSERT",
          purchase,
          """
          {"type": "INPUT_PURCHASE", "propertyId": "%s", "performedOn": "%s",
           "inputOtherName": "Esterco", "quantity": {"value": 2, "unit": "BAG"}}\
          """
              .formatted(propertyId, today)),
    };
    var first = push(farmer, batch).andExpect(status().isOk());
    List<String> statuses = read(first, "$.results[*].status");
    assertThat(statuses).containsExactly("APPLIED", "APPLIED", "APPLIED");

    // Simula timeout depois do commit: o app reenvia o mesmo lote.
    var second = push(farmer, batch).andExpect(status().isOk());
    assertThat(second.andReturn().getResponse().getContentAsString())
        .contains(statuses.getFirst())
        .contains(planting.toString());
    List<Object> versions1 = read(first, "$.results[*].version");
    List<Object> versions2 = read(second, "$.results[*].version");
    assertThat(versions2).isEqualTo(versions1);

    var changes = pull(farmer, "0", 500);
    List<String> ids = read(changes, "$.changes.records[*].id");
    assertThat(ids)
        .containsExactlyInAnyOrder(planting.toString(), harvest.toString(), purchase.toString());
    List<Integer> numbers = read(changes, "$.changes.records[*].number");
    assertThat(numbers).doesNotContainNull().doesNotHaveDuplicates();
    // RN11 + RN27: a colheita herdou a área do plantio; a compra não tem área.
    assertThat((List<String>) read(changes, "$.changes.records[?(@.type == 'HARVEST')].plotId"))
        .containsExactly(plotId.toString());
    assertThat(
            (List<Object>) read(changes, "$.changes.records[?(@.type == 'INPUT_PURCHASE')].plotId"))
        .containsExactly((Object) null);
  }

  @Test
  void case4_harvestOfARejectedPlanting_isAlsoRejected() throws Exception {
    UUID planting = UUID.randomUUID();
    UUID harvest = UUID.randomUUID();
    var result =
        push(
            farmer,
            mutation(
                UUID.randomUUID(),
                "record",
                "UPSERT",
                planting,
                plantingData(planting, UUID.randomUUID(), today)),
            mutation(
                UUID.randomUUID(),
                "record",
                "UPSERT",
                harvest,
                harvestData(harvest, planting, today)));
    assertThat((List<String>) read(result, "$.results[*].status"))
        .containsExactly("REJECTED", "REJECTED");
    assertThat((String) read(result, "$.results[0].error.errors[0].code"))
        .isEqualTo("PLOT_NOT_FOUND");
    assertThat((String) read(result, "$.results[1].error.errors[0].code"))
        .isEqualTo("PLANTING_NOT_FOUND");

    // Corrigido o plantio (mutação nova), a colheita reenviada passa.
    push(
            farmer,
            mutation(
                UUID.randomUUID(),
                "record",
                "UPSERT",
                planting,
                plantingData(planting, plotId, today)))
        .andExpect(jsonPath("$.results[0].status").value("APPLIED"));
    push(
            farmer,
            mutation(
                UUID.randomUUID(),
                "record",
                "UPSERT",
                harvest,
                harvestData(harvest, planting, today)))
        .andExpect(jsonPath("$.results[0].status").value("APPLIED"));
  }

  @Test
  void case5_farmerOverwritesCoordinatorEdit_lastWins_andCoordinatorIsNotified_ADR08()
      throws Exception {
    UUID planting = UUID.randomUUID();
    push(
        farmer,
        mutation(
            UUID.randomUUID(),
            "record",
            "UPSERT",
            planting,
            plantingData(planting, plotId, today)));
    long version =
        ((Number)
                read(
                    call(HttpMethod.GET, "/records/" + planting, coordinator.accessToken(), null),
                    "$.version"))
            .longValue();
    call(
            HttpMethod.PUT,
            "/records/" + planting,
            coordinator.accessToken(),
            """
            {"type": "PLANTING", "propertyId": "%s", "plotId": "%s", "performedOn": "%s",
             "cropOtherName": "Feijão (corrigido)", "version": %d}\
            """
                .formatted(propertyId, plotId, today, version))
        .andExpect(status().isOk());

    push(
            farmer,
            mutation(
                UUID.randomUUID(),
                "record",
                "UPSERT",
                planting,
                plantingData(planting, plotId, today)))
        .andExpect(jsonPath("$.results[0].status").value("APPLIED"));

    call(HttpMethod.GET, "/records/" + planting, coordinator.accessToken(), null)
        .andExpect(jsonPath("$.cropOtherName").value("Feijão de corda"));
    call(HttpMethod.GET, "/notifications?unreadOnly=true", coordinator.accessToken(), null)
        .andExpect(jsonPath("$.content[0].type").value("RECORD_OVERWRITTEN_BY_FARMER"));
    List<String> farmerNotes = read(pull(farmer, "0", 500), "$.changes.notifications[*].type");
    assertThat(farmerNotes).contains("RECORD_CHANGED_BY_ASSOCIATION"); // RN16
  }

  @Test
  void case6_deleteOnTheWeb_sendsATombstone() throws Exception {
    UUID planting = UUID.randomUUID();
    push(
        farmer,
        mutation(
            UUID.randomUUID(),
            "record",
            "UPSERT",
            planting,
            plantingData(planting, plotId, today)));
    String cursor = read(pull(farmer, "0", 500), "$.cursor");

    call(HttpMethod.DELETE, "/records/" + planting, coordinator.accessToken(), null)
        .andExpect(status().isNoContent());

    var next = pull(farmer, cursor, 500);
    assertThat((List<String>) read(next, "$.deleted[?(@.entity == 'record')].id"))
        .containsExactly(planting.toString());
    assertThat((List<String>) read(next, "$.changes.records[*].id")).isEmpty();
    // Não ressuscita: um UPSERT depois da exclusão é rejeitado.
    push(
            farmer,
            mutation(
                UUID.randomUUID(),
                "record",
                "UPSERT",
                planting,
                plantingData(planting, plotId, today)))
        .andExpect(jsonPath("$.results[0].error.code").value("NOT_FOUND"));
  }

  @Test
  void case8_pullWithMoreChangesThanTheLimit_comesInPages_withoutLosingRows() throws Exception {
    var mutations = new java.util.ArrayList<String>();
    var expected = new java.util.HashSet<String>();
    for (int i = 0; i < 7; i++) {
      UUID id = UUID.randomUUID();
      expected.add(id.toString());
      mutations.add(
          mutation(UUID.randomUUID(), "record", "UPSERT", id, plantingData(id, plotId, today)));
    }
    push(farmer, mutations.toArray(String[]::new));

    var seen = new java.util.HashSet<String>();
    String cursor = "0";
    boolean hasMore = true;
    int pages = 0;
    while (hasMore && pages < 1000) {
      var page = pull(farmer, cursor, 3);
      seen.addAll(read(page, "$.changes.records[*].id"));
      cursor = read(page, "$.cursor");
      hasMore = read(page, "$.hasMore");
      pages++;
    }
    assertThat(hasMore).as("o pull precisa terminar").isFalse();
    assertThat(pages).isGreaterThan(2);
    assertThat(seen).containsAll(expected);
  }

  @Test
  void case9_concurrentWrites_areAllSeenByThePull_ADR09() throws Exception {
    String cursor = read(pull(farmer, "0", 500), "$.cursor");
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    var fa = CompletableFuture.runAsync(() -> pushQuietly(a));
    var fb = CompletableFuture.runAsync(() -> pushQuietly(b));
    CompletableFuture.allOf(fa, fb).join();
    List<String> ids = read(pull(farmer, cursor, 500), "$.changes.records[*].id");
    assertThat(ids).contains(a.toString(), b.toString());
  }

  private void pushQuietly(UUID id) {
    try {
      push(
              farmer,
              mutation(UUID.randomUUID(), "record", "UPSERT", id, plantingData(id, plotId, today)))
          .andExpect(jsonPath("$.results[0].status").value("APPLIED"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void case10_photoUpload_isIdempotent_checksTheHash_andCountsAsReceipt() throws Exception {
    UUID purchase = UUID.randomUUID();
    push(
        farmer,
        mutation(
            UUID.randomUUID(),
            "record",
            "UPSERT",
            purchase,
            """
            {"type": "INPUT_PURCHASE", "propertyId": "%s", "performedOn": "%s",
             "inputOtherName": "Calcário", "quantity": {"value": 1, "unit": "BAG"}}\
            """
                .formatted(propertyId, today)));
    byte[] jpeg = tinyJpeg();
    String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jpeg));
    UUID attachment = UUID.randomUUID();

    // Antes do metadado sincronizar, o upload responde ATTACHMENT_NOT_FOUND.
    upload(attachment, jpeg).andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_FOUND"));

    push(
            farmer,
            mutation(
                UUID.randomUUID(),
                "attachment",
                "UPSERT",
                attachment,
                """
                {"ownerType": "RECORD", "ownerId": "%s", "kind": "PHOTO",
                 "contentType": "image/jpeg", "sha256": "%s"}\
                """
                    .formatted(purchase, sha)))
        .andExpect(jsonPath("$.results[0].status").value("APPLIED"));

    byte[] corrupted = jpeg.clone();
    corrupted[corrupted.length - 3] ^= 1;
    upload(attachment, corrupted).andExpect(jsonPath("$.code").value("CHECKSUM_MISMATCH"));
    upload(attachment, jpeg).andExpect(status().isNoContent());
    upload(attachment, jpeg).andExpect(status().isNoContent()); // repetir é seguro

    mockMvc
        .perform(
            get("/files/" + attachment + "/content")
                .header("Authorization", "Bearer " + farmer.accessToken()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/jpeg"))
        .andExpect(content().bytes(jpeg));

    // A foto conta como nota fiscal: a compra fica completa (doc 1 §2.5).
    call(HttpMethod.GET, "/records/" + purchase, coordinator.accessToken(), null)
        .andExpect(jsonPath("$.completeness").value("COMPLETE"))
        .andExpect(jsonPath("$.attachments[0].uploaded").value(true));
  }

  @Test
  void otherFarmer_cannotSeeOrTouchMyData_IDOR() throws Exception {
    UUID planting = UUID.randomUUID();
    push(
        farmer,
        mutation(
            UUID.randomUUID(),
            "record",
            "UPSERT",
            planting,
            plantingData(planting, plotId, today)));
    var intruder = fixtures.farmer("Intruso");

    push(intruder, mutation(UUID.randomUUID(), "record", "DELETE", planting, null))
        .andExpect(jsonPath("$.results[0].status").value("REJECTED"))
        .andExpect(jsonPath("$.results[0].error.code").value("NOT_FOUND"));
    push(
            intruder,
            mutation(
                UUID.randomUUID(),
                "record",
                "UPSERT",
                planting,
                plantingData(planting, plotId, today)))
        .andExpect(jsonPath("$.results[0].status").value("REJECTED"));

    var theirPull = pull(intruder, "0", 500);
    assertThat((List<String>) read(theirPull, "$.changes.records[*].id")).isEmpty();
    assertThat((List<String>) read(theirPull, "$.changes.properties[*].id")).isEmpty();
    // A pessoa do outro agricultor não aparece em "people" (só ele mesmo e os coordenadores).
    List<Map<String, Object>> people = read(theirPull, "$.changes.people");
    assertThat(people).noneMatch(p -> farmer.user().getId().toString().equals(p.get("id")));
  }

  @Test
  void farmerCreatesPropertyOffline_andGetsTheMainPlot_RN06() throws Exception {
    UUID property = UUID.randomUUID();
    push(
            farmer,
            mutation(
                UUID.randomUUID(),
                "property",
                "UPSERT",
                property,
                """
                {"id": "%s", "name": "Sítio da Lagoa", "farmerId": "%s",
                 "totalArea": {"value": 1, "unit": "HECTARE"}}\
                """
                    .formatted(property, UUID.randomUUID())))
        .andExpect(jsonPath("$.results[0].status").value("APPLIED"));
    var changes = pull(farmer, "0", 500);
    List<Map<String, Object>> props =
        read(changes, "$.changes.properties[?(@.id == '%s')]".formatted(property));
    assertThat(props)
        .singleElement()
        .satisfies(p -> assertThat(p.get("farmerId")).isEqualTo(farmer.user().getId().toString()));
    List<String> plotNames =
        read(changes, "$.changes.plots[?(@.propertyId == '%s')].name".formatted(property));
    assertThat(plotNames).containsExactly("Área principal");
    assertThat((String) read(changes, "$.changes.me.name")).isEqualTo("João do Feijão");
    assertThat((List<String>) read(changes, "$.changes.units[*].code")).contains("TAREFA", "BUNCH");
  }

  private ResultActions upload(UUID attachment, byte[] bytes) throws Exception {
    return mockMvc.perform(
        put("/files/" + attachment + "/content")
            .header("Authorization", "Bearer " + farmer.accessToken())
            .contentType(MediaType.IMAGE_JPEG)
            .content(bytes));
  }

  /** Um JPEG mínimo (cabeçalho JFIF), suficiente para a detecção de tipo. */
  private static byte[] tinyJpeg() {
    return HexFormat.of()
        .parseHex(
            "ffd8ffe000104a46494600010100000100010000ffdb004300080606070605080707070909080a0c140d0c0b0b0c1912130f141d1a1f1e1d1a1c1c20242e2720222c231c1c2837292c30313434341f27393d38323c2e333432ffc0000b080001000101011100ffc4001f0000010501010101010100000000000000000102030405060708090a0bffda0008010100003f00d2cf20ffd9");
  }
}
