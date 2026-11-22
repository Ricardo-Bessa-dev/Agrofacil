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
import org.springframework.test.web.servlet.ResultActions;

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
  void outflow_canBeLinkedToAFinishedPlanting_RN42() throws Exception {
    String plantingId = planting(today.minusDays(30).toString());
    finalHarvest(plantingId, today.minusDays(2));

    outflow(plantingId, today)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.plantingId").value(plantingId));
  }

  @Test
  void outflow_canBeRegisteredWithoutHarvest_onThePlantingDate_RN42() throws Exception {
    LocalDate plantedOn = today.minusDays(30);
    String plantingId = planting(plantedOn.toString());

    outflow(plantingId, plantedOn).andExpect(status().isCreated());
  }

  @Test
  void outflowBeforePlanting_isRejected_RN42() throws Exception {
    LocalDate plantedOn = today.minusDays(30);
    String plantingId = planting(plantedOn.toString());

    outflow(plantingId, plantedOn.minusDays(1))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("performedOn"))
        .andExpect(jsonPath("$.errors[0].code").value("BEFORE_PLANTING"));
  }

  @Test
  void list_showsTheCropAndTheCycleOfPlantingsOnly_RN42() throws Exception {
    String plantingId = planting(today.minusDays(30).toString());
    LocalDate finishedOn = today.minusDays(2);
    finalHarvest(plantingId, finishedOn);

    String url = "/records?propertyId=" + propertyId + "&type=";
    call(HttpMethod.GET, url + "PLANTING", coordinator.accessToken(), null)
        .andExpect(jsonPath("$.content[0].planting.cropOtherName").value("Alface crespa"))
        .andExpect(jsonPath("$.content[0].planting.cycleStatus").value("FINISHED"))
        .andExpect(jsonPath("$.content[0].planting.finishedOn").value(finishedOn.toString()));
    call(HttpMethod.GET, url + "HARVEST", coordinator.accessToken(), null)
        .andExpect(jsonPath("$.content[0].id").exists())
        .andExpect(jsonPath("$.content[0].planting").doesNotExist());
  }

  private void finalHarvest(String plantingId, LocalDate performedOn) throws Exception {
    call(
            HttpMethod.POST,
            "/records",
            coordinator.accessToken(),
            """
            {"type": "HARVEST", "propertyId": "%s", "plantingId": "%s", "performedOn": "%s",
             "isFinal": true}\
            """
                .formatted(propertyId, plantingId, performedOn))
        .andExpect(status().isCreated());
  }

  private ResultActions outflow(String plantingId, LocalDate performedOn) throws Exception {
    return call(
        HttpMethod.POST,
        "/records",
        coordinator.accessToken(),
        """
        {"type": "OUTFLOW", "propertyId": "%s", "plantingId": "%s", "performedOn": "%s",
         "productOtherName": "Alface crespa"}\
        """
            .formatted(propertyId, plantingId, performedOn));
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
  void plotsMustFitInTheProperty_andALoneMainPlotFollowsItsSize_RN41() throws Exception {
    String plotsPath = "/properties/" + propertyId + "/plots";
    // Sozinha e do tamanho da propriedade, a "Área principal" acompanha a correção do tamanho.
    call(
            HttpMethod.PATCH,
            "/properties/" + propertyId,
            coordinator.accessToken(),
            "{\"totalArea\": {\"value\": 4, \"unit\": \"TAREFA\"}, \"version\": 0}")
        .andExpect(status().isOk());
    var plots =
        call(HttpMethod.GET, plotsPath, coordinator.accessToken(), null)
            .andExpect(jsonPath("$[0].area.value").value(4));
    Integer mainVersion = read(plots, "$[0].version");

    String newPlot =
        "{\"name\": \"Roça de baixo\", \"status\": \"ORGANIC\","
            + " \"area\": {\"value\": 1, \"unit\": \"TAREFA\"}}";
    call(HttpMethod.POST, plotsPath, coordinator.accessToken(), newPlot)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("area"))
        .andExpect(jsonPath("$.errors[0].code").value("EXCEEDS_PROPERTY_AREA"));

    call(
            HttpMethod.PATCH,
            "/plots/" + plotId,
            coordinator.accessToken(),
            "{\"area\": {\"value\": 3, \"unit\": \"TAREFA\"}, \"version\": %d}"
                .formatted(mainVersion))
        .andExpect(status().isOk());
    call(HttpMethod.POST, plotsPath, coordinator.accessToken(), newPlot)
        .andExpect(status().isCreated());

    // Com duas áreas ocupando 4 tarefas, a propriedade não pode encolher para 3.
    call(
            HttpMethod.PATCH,
            "/properties/" + propertyId,
            coordinator.accessToken(),
            "{\"totalArea\": {\"value\": 3, \"unit\": \"TAREFA\"}, \"version\": 1}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].code").value("SMALLER_THAN_PLOTS"));
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

  @Test
  void linkedOutflow_withoutProduct_usesThePlantingProduct_RN42() throws Exception {
    var plantingId = planting(today.toString());
    call(
            HttpMethod.POST,
            "/records",
            coordinator.accessToken(),
            """
            {"type":"OUTFLOW","propertyId":"%s","performedOn":"%s",
             "plantingId":"%s",
             "quantity":{"value":2.5,"unit":"KILOGRAM"},"destination":"FAIR"}
            """
                .formatted(propertyId, today, plantingId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.productOtherName").value("Alface crespa"))
        .andExpect(jsonPath("$.quantity.unit").value("KILOGRAM"));
  }

  @Test
  void linkedOutflow_keepsTheInformedProduct_RN42() throws Exception {
    var plantingId = planting(today.toString());
    call(
            HttpMethod.POST,
            "/records",
            coordinator.accessToken(),
            """
            {"type":"OUTFLOW","propertyId":"%s","performedOn":"%s",
             "plantingId":"%s","productOtherName":"Alface higienizada"}
            """
                .formatted(propertyId, today, plantingId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.plantingId").value(plantingId))
        .andExpect(jsonPath("$.productOtherName").value("Alface higienizada"));
  }
}
