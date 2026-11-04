// Painel inicial e relatórios: números do painel, caderno de campo em PDF/CSV e escopo do
// agricultor.
package br.edu.ifba.agrofacil.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.ifba.agrofacil.IntegrationTest;
import br.edu.ifba.agrofacil.support.Fixtures;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class ReportsAndDashboardTest extends IntegrationTest {

  private Fixtures.Coordinator coordinator;
  private Fixtures.Farmer farmer;
  private String propertyId;
  private String plotId;

  @BeforeEach
  void scenario() throws Exception {
    coordinator = fixtures.coordinator();
    farmer = fixtures.farmer("Joana Licuri");
    propertyId = fixtures.property(coordinator, farmer, "Sítio São João").id().toString();
    plotId =
        read(
            call(
                HttpMethod.GET,
                "/properties/" + propertyId + "/plots",
                coordinator.accessToken(),
                null),
            "$[0].id");
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of("America/Bahia")));
  }

  private String record(String json) throws Exception {
    return read(
        call(HttpMethod.POST, "/records", coordinator.accessToken(), json)
            .andExpect(status().isCreated()),
        "$.id");
  }

  private String planting(String crop) throws Exception {
    return record(
        """
        {"type": "PLANTING", "propertyId": "%s", "plotId": "%s", "performedOn": "%s",
         "cropOtherName": "%s"}\
        """
            .formatted(propertyId, plotId, today().minusDays(20), crop));
  }

  private byte[] download(String path, String token) throws Exception {
    return call(HttpMethod.GET, path, token, null)
        .andExpect(status().isOk())
        .andExpect(
            header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
        .andReturn()
        .getResponse()
        .getContentAsByteArray();
  }

  @Test
  void dashboard_countsFarmersWithoutRecords_andListsTwelveWeeks() throws Exception {
    call(HttpMethod.GET, "/dashboard/summary", coordinator.accessToken(), null)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.farmers.withoutRecordsLast30Days")
                .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.records.perWeek.length()").value(12))
        .andExpect(jsonPath("$.records.byTypeLast30Days.PLANTING").exists())
        .andExpect(
            jsonPath("$.certifications.NONE").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.visits.urgent").exists())
        .andExpect(jsonPath("$.generatedAt").exists());
    call(HttpMethod.GET, "/dashboard/summary", farmer.accessToken(), null)
        .andExpect(status().isForbidden());
  }

  @Test
  void fieldNotebookPdf_groupsHarvestsUnderThePlanting_withAccents() throws Exception {
    var planting = planting("Feijão de corda");
    record(
        """
        {"type": "HARVEST", "propertyId": "%s", "plantingId": "%s", "performedOn": "%s",
         "quantity": {"value": 30, "unit": "BAG"}, "isFinal": false}\
        """
            .formatted(propertyId, planting, today().minusDays(2)));

    byte[] pdf =
        download(
            "/reports/field-notebook?format=pdf&propertyId=" + propertyId,
            coordinator.accessToken());
    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    String text;
    try (var doc = Loader.loadPDF(pdf)) {
      text = new PDFTextStripper().getText(doc);
    }
    assertThat(text)
        .contains("Caderno de campo", "Sítio São João", "Feijão de corda", "Colheita", "30 sacos")
        .contains("Gerado pelo Agrofácil em", "Página 1 de");
  }

  @Test
  void fieldNotebook_farmerGetsOnlyTheirOwn_asCsv() throws Exception {
    planting("Coentro");
    byte[] csv =
        download(
            "/reports/field-notebook?format=csv&propertyId=" + propertyId, farmer.accessToken());
    String body = new String(csv, StandardCharsets.UTF_8);
    assertThat(body)
        .startsWith("﻿Nº;Data;Tipo;Área;Item")
        .contains(";Plantio;Área principal;Coentro;");

    var other = fixtures.farmer("Outro Agricultor");
    call(
            HttpMethod.GET,
            "/reports/field-notebook?propertyId=" + propertyId,
            other.accessToken(),
            null)
        .andExpect(status().isNotFound());
  }

  @Test
  void recordsCsv_neutralizesFormulas_andMembersAndCertificationsWork() throws Exception {
    planting("=1+1");
    String records =
        new String(
            download(
                "/reports/records?format=csv&propertyId=" + propertyId, coordinator.accessToken()),
            StandardCharsets.UTF_8);
    assertThat(records).contains(";'=1+1;").contains("Joana Licuri");

    String members =
        new String(
            download("/reports/members?format=csv", coordinator.accessToken()),
            StandardCharsets.UTF_8);
    assertThat(members).contains("Joana Licuri;Ativo;;Sítio São João;Sem certificação");

    byte[] certs = download("/reports/certifications?format=pdf", coordinator.accessToken());
    assertThat(new String(certs, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    call(HttpMethod.GET, "/reports/records?format=pdf", coordinator.accessToken(), null)
        .andExpect(status().isUnprocessableContent());
    List.of("/reports/members", "/reports/certifications", "/reports/records")
        .forEach(path -> {
          try {
            call(HttpMethod.GET, path, farmer.accessToken(), null)
                .andExpect(status().isForbidden());
          } catch (Exception e) {
            throw new AssertionError(e);
          }
        });
  }
}
