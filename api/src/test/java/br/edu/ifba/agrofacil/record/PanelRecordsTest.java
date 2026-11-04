// Propriedades e registros pelo painel: área principal, completude, datas, ciclo do plantio e
// histórico.
package br.edu.ifba.agrofacil.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.ifba.agrofacil.IntegrationTest;
import br.edu.ifba.agrofacil.support.Fixtures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class PanelRecordsTest extends IntegrationTest {

  private Fixtures.Coordinator coordinator;
  private Fixtures.Farmer farmer;
  private String propertyId;
  private String plotId;
  private final LocalDate today = LocalDate.now();

  @BeforeEach
  void scenario() throws Exception {
    coordinator = fixtures.coordinator();
    farmer = fixtures.farmer("Maria do Umbu");
    var created =
        call(
                HttpMethod.POST,
                "/properties",
                coordinator.accessToken(),
                """
                {"farmerId": "%s", "name": "Sítio Boa Vista",
                 "totalArea": {"value": 3, "unit": "TAREFA"},
                 "address": {"municipalityId": 2914604}}\
                """
                    .formatted(farmer.user().getId()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalArea.m2").value(13068))
            .andExpect(jsonPath("$.address.municipalityName").value("Irecê"));
    propertyId = read(created, "$.id");
    var plots =
        call(
                HttpMethod.GET,
                "/properties/" + propertyId + "/plots",
                coordinator.accessToken(),
                null)
            .andExpect(status().isOk());
    List<String> names = read(plots, "$[*].name");
    assertThat(names).containsExactly("Área principal"); // RN06
    plotId = read(plots, "$[0].id");
  }

  private String planting(String performedOn) throws Exception {
    var result =
        call(
                HttpMethod.POST,
                "/records",
                coordinator.accessToken(),
                """
                {"type": "PLANTING", "propertyId": "%s", "plotId": "%s", "performedOn": "%s",
                 "cropOtherName": "Alface crespa", "plantedArea": {"value": 0.5, "unit": "TAREFA"}}\
                """
                    .formatted(propertyId, plotId, performedOn))
            .andExpect(status().isCreated());
    return read(result, "$.id");
  }

  @Test
  void planting_isNumbered_andListsWhatIsMissing_RN09_RN14() throws Exception {
    var result =
        call(
                HttpMethod.POST,
                "/records",
                coordinator.accessToken(),
                """
                {"type": "PLANTING", "propertyId": "%s", "plotId": "%s", "performedOn": "%s",
                 "cropOtherName": "Coentro", "plantedArea": {"value": 0.5, "unit": "TAREFA"}}\
                """
                    .formatted(propertyId, plotId, today.minusDays(3)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.number").isNumber())
            .andExpect(jsonPath("$.farmerId").value(farmer.user().getId().toString()))
            .andExpect(jsonPath("$.plantedArea.m2").value(2178))
            .andExpect(jsonPath("$.completeness").value("INCOMPLETE"))
            .andExpect(jsonPath("$.cycleStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.source").value("WEB"));
    List<String> missing = read(result, "$.missingFields");
    assertThat(missing).containsExactly("seedOrigin", "expectedHarvestOn");
  }

  @Test
  void harvestBeforePlanting_isRejected_RN10() throws Exception {
    String plantingId = planting(today.minusDays(5).toString());
    call(
            HttpMethod.POST,
            "/records",
            coordinator.accessToken(),
            """
            {"type": "HARVEST", "propertyId": "%s", "plantingId": "%s", "performedOn": "%s"}\
            """
                .formatted(propertyId, plantingId, today.minusDays(10)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].code").value("BEFORE_PLANTING"));
  }

  @Test
  void quantityInAreaUnit_isRejected() throws Exception {
    String plantingId = planting(today.minusDays(5).toString());
    call(
            HttpMethod.POST,
            "/records",
            coordinator.accessToken(),
            """
            {"type": "HARVEST", "propertyId": "%s", "plantingId": "%s", "performedOn": "%s",
             "quantity": {"value": 2, "unit": "HECTARE"}}\
            """
                .formatted(propertyId, plantingId, today))
        .andExpect(jsonPath("$.errors[0].field").value("quantity.unit"))
        .andExpect(jsonPath("$.errors[0].code").value("UNIT_NOT_ALLOWED"));
  }

  @Test
  void finalHarvest_finishesThePlanting_andDeletingItReopens_RN12() throws Exception {
    String plantingId = planting(today.minusDays(30).toString());
    var harvest =
        call(
                HttpMethod.POST,
                "/records",
                coordinator.accessToken(),
                """
                {"type": "HARVEST", "propertyId": "%s", "plantingId": "%s", "performedOn": "%s",
                 "quantity": {"value": 30, "unit": "BUNCH"}, "isFinal": true}\
                """
                    .formatted(propertyId, plantingId, today.minusDays(2)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.plotId").value(plotId)) // copiado do plantio (RN11)
            .andExpect(jsonPath("$.completeness").value("COMPLETE"));
    String harvestId = read(harvest, "$.id");

    call(HttpMethod.GET, "/records/" + plantingId, coordinator.accessToken(), null)
        .andExpect(jsonPath("$.cycleStatus").value("FINISHED"))
        .andExpect(jsonPath("$.finishedOn").value(today.minusDays(2).toString()));

    // RN13: o plantio com colheita não pode ser excluído.
    call(HttpMethod.DELETE, "/records/" + plantingId, coordinator.accessToken(), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PLANTING_HAS_DEPENDENTS"));

    call(HttpMethod.DELETE, "/records/" + harvestId, coordinator.accessToken(), null)
        .andExpect(status().isNoContent());
    call(HttpMethod.GET, "/records/" + plantingId, coordinator.accessToken(), null)
        .andExpect(jsonPath("$.cycleStatus").value("ACTIVE"));
    call(HttpMethod.DELETE, "/records/" + plantingId, coordinator.accessToken(), null)
        .andExpect(status().isNoContent());
  }

  @Test
  void update_withStaleVersion_conflicts_andHistoryShowsTheChanges() throws Exception {
    String plantingId = planting(today.minusDays(4).toString());
    String update =
        """
        {"type": "PLANTING", "propertyId": "%s", "plotId": "%s", "performedOn": "%s",
         "cropOtherName": "Alface roxa", "seedOrigin": "OWN", "version": %d}\
        """;
    long version =
        ((Number)
                read(
                    call(HttpMethod.GET, "/records/" + plantingId, coordinator.accessToken(), null),
                    "$.version"))
            .longValue();
    call(
            HttpMethod.PUT,
            "/records/" + plantingId,
            coordinator.accessToken(),
            update.formatted(propertyId, plotId, today.minusDays(4), version))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cropOtherName").value("Alface roxa"));
    call(
            HttpMethod.PUT,
            "/records/" + plantingId,
            coordinator.accessToken(),
            update.formatted(propertyId, plotId, today.minusDays(4), version))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

    var history =
        call(HttpMethod.GET, "/records/" + plantingId + "/history", coordinator.accessToken(), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].changeType").value("CREATED"))
            .andExpect(jsonPath("$[0].source").value("WEB"))
            .andExpect(jsonPath("$[0].by.role").value("COORDINATOR"));
    List<String> lastChanged = read(history, "$[-1].changes[*].field");
    assertThat(lastChanged).contains("cropOtherName", "seedOrigin").doesNotContain("version");
  }

  @Test
  void list_filtersByType_andResolvesTheSubject() throws Exception {
    planting(today.minusDays(6).toString());
    call(
            HttpMethod.GET,
            "/records?type=PLANTING&propertyId=" + propertyId,
            coordinator.accessToken(),
            null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].subject").value("Alface crespa"))
        .andExpect(jsonPath("$.content[0].farmer.name").value("Maria do Umbu"))
        .andExpect(jsonPath("$.content[0].plot.name").value("Área principal"));
  }

  @Test
  void farmerList_showsIndicators_andSearchIgnoresAccents() throws Exception {
    planting(today.minusDays(1).toString());
    call(HttpMethod.GET, "/farmers?search=MARIA DO UMBU", coordinator.accessToken(), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].propertiesCount").value(1))
        .andExpect(jsonPath("$.content[0].incompleteRecords").value(1))
        .andExpect(jsonPath("$.content[0].worstCertificationStatus").value("NONE"))
        .andExpect(
            jsonPath("$.content[0].cpfMasked").value(org.hamcrest.Matchers.startsWith("***.")));
    call(HttpMethod.GET, "/municipalities?search=irece", coordinator.accessToken(), null)
        .andExpect(jsonPath("$[0].name").value("Irecê"));
  }

  @Test
  void property_patchNeedsCurrentVersion_andLastPlotCannotBeDeleted_RN08() throws Exception {
    call(
            HttpMethod.PATCH,
            "/properties/" + propertyId,
            coordinator.accessToken(),
            "{\"name\": \"Outro\", \"version\": 99}")
        .andExpect(status().isConflict());
    call(
            HttpMethod.PATCH,
            "/properties/" + propertyId,
            coordinator.accessToken(),
            "{\"name\": \"Sítio Novo\", \"version\": 0}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Sítio Novo"))
        .andExpect(jsonPath("$.address.municipalityId").value(2914604));
    call(HttpMethod.DELETE, "/plots/" + plotId, coordinator.accessToken(), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LAST_PLOT"));
    call(
            HttpMethod.POST,
            "/properties/" + propertyId + "/plots",
            coordinator.accessToken(),
            "{\"name\": \"área principal\", \"status\": \"ORGANIC\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PLOT_NAME_IN_USE"));
  }
}
