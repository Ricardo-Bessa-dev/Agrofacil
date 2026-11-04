// Gestão dos catálogos pelo coordenador e associação dos itens sem catálogo (ADR-07, RN22).
package br.edu.ifba.agrofacil.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.ifba.agrofacil.IntegrationTest;
import br.edu.ifba.agrofacil.support.Fixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CatalogAdminTest extends IntegrationTest {

  private Fixtures.Coordinator coordinator;
  private String unique;

  @BeforeEach
  void scenario() {
    coordinator = fixtures.coordinator();
    unique = Long.toString(System.nanoTime(), 36);
  }

  private String createCrop(String name) throws Exception {
    return read(
        call(
                HttpMethod.POST,
                "/catalog/crops",
                coordinator.accessToken(),
                """
                {"name": "%s", "icon": "fruit"}\
                """
                    .formatted(name))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.global").value(false))
            .andExpect(jsonPath("$.active").value(true)),
        "$.id");
  }

  @Test
  void crop_isCreated_rejectsDuplicateName_andLeavesUseWithActiveFalse() throws Exception {
    var id = createCrop("Pitaia " + unique);
    call(
            HttpMethod.POST,
            "/catalog/crops",
            coordinator.accessToken(),
            """
            {"name": "  PITAIA %s "}\
            """
                .formatted(unique))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE"));
    call(
            HttpMethod.PATCH,
            "/catalog/crops/" + id,
            coordinator.accessToken(),
            """
            {"active": false, "version": 0}\
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    List<String> active =
        read(call(HttpMethod.GET, "/catalog/crops", coordinator.accessToken(), null), "$[*].id");
    List<String> all =
        read(
            call(
                HttpMethod.GET,
                "/catalog/crops?includeInactive=true",
                coordinator.accessToken(),
                null),
            "$[*].id");
    assertThat(active).doesNotContain(id);
    assertThat(all).contains(id);
  }

  @Test
  void farmInput_isCreatedAndEdited() throws Exception {
    var id =
        read(
            call(
                    HttpMethod.POST,
                    "/catalog/farm-inputs",
                    coordinator.accessToken(),
                    """
                    {"name": "Calda %s", "inputType": "PEST_DISEASE_CONTROL", "homeMade": true}\
                    """
                        .formatted(unique))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.homeMade").value(true))
                .andExpect(jsonPath("$.needsAuthorization").value(false)),
            "$.id");
    call(
            HttpMethod.PATCH,
            "/catalog/farm-inputs/" + id,
            coordinator.accessToken(),
            """
            {"needsAuthorization": true, "version": 0}\
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.needsAuthorization").value(true))
        .andExpect(jsonPath("$.homeMade").value(true));
  }

  @Test
  void unit_onlyChangesActiveAndOrder() throws Exception {
    call(
            HttpMethod.PATCH,
            "/catalog/units/DOZEN",
            coordinator.accessToken(),
            """
            {"sortOrder": 77}\
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sortOrder").value(77))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.nameSingular").value("dúzia"));
    call(HttpMethod.PATCH, "/catalog/units/NOPE", coordinator.accessToken(), "{}")
        .andExpect(status().isNotFound());
  }

  @Test
  void unmappedText_isGroupedIgnoringCase_andResolvingUpdatesEveryRecord_RN22() throws Exception {
    var farmer = fixtures.farmer("Lia do Mandacaru");
    var propertyId = fixtures.property(coordinator, farmer, "Sítio Mandacaru").id();
    String plotId =
        read(
            call(
                HttpMethod.GET,
                "/properties/" + propertyId + "/plots",
                coordinator.accessToken(),
                null),
            "$[0].id");
    String text = "Pitaia roxa " + unique;
    String[] variants = {text, "  " + text.toUpperCase() + " "};
    String recordId = null;
    for (String variant : variants) {
      recordId =
          read(
              call(
                      HttpMethod.POST,
                      "/records",
                      coordinator.accessToken(),
                      """
                      {"type": "PLANTING", "propertyId": "%s", "plotId": "%s",
                       "performedOn": "%s", "cropOtherName": "%s"}\
                      """
                          .formatted(propertyId, plotId, LocalDate.now().minusDays(2), variant))
                  .andExpect(status().isCreated()),
              "$.id");
    }

    var list =
        call(HttpMethod.GET, "/catalog/unmapped?kind=CROP", coordinator.accessToken(), null)
            .andExpect(status().isOk());
    List<Map<String, Object>> items = read(list, "$[*]");
    assertThat(items)
        .filteredOn(i -> text.equalsIgnoreCase((String) i.get("text")))
        .extracting(i -> i.get("occurrences"))
        .containsExactly(2);

    var cropId = createCrop("Pitaia " + unique);
    call(
            HttpMethod.POST,
            "/catalog/unmapped/resolve",
            coordinator.accessToken(),
            """
            {"kind": "CROP", "text": "%s", "targetId": "%s"}\
            """
                .formatted(text.toLowerCase(), cropId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updatedRecords").value(2));

    call(HttpMethod.GET, "/records/" + recordId, coordinator.accessToken(), null)
        .andExpect(jsonPath("$.cropId").value(cropId))
        .andExpect(jsonPath("$.cropOtherName").doesNotExist());
    List<String> texts =
        read(
            call(HttpMethod.GET, "/catalog/unmapped?kind=CROP", coordinator.accessToken(), null),
            "$[*].text");
    assertThat(texts).noneMatch(text::equalsIgnoreCase);
  }
}
