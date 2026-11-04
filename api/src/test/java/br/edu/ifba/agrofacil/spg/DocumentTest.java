// Documentos do SPG: geração dos 8 modelos, substituição, pré-condições, decisão em branco,
// conferência pelo código, cópia assinada e escopo (RN37, RN38, ADR-15).
package br.edu.ifba.agrofacil.spg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class DocumentTest extends SpgScenarioTest {

  private String generate(String json) throws Exception {
    return read(
        call(HttpMethod.POST, "/documents", coordinator.accessToken(), json)
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.verificationCode")
                    .value(
                        org.hamcrest.Matchers.matchesPattern(
                            "[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}")))
            .andExpect(jsonPath("$.status").value("GENERATED")),
        "$.id");
  }

  private String pdfText(String documentId) throws Exception {
    byte[] pdf =
        call(
                HttpMethod.GET,
                "/documents/" + documentId + "/content",
                coordinator.accessToken(),
                null)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var doc = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(doc);
    }
  }

  @Test
  void conformityDecision_twice_supersedesTheFirst_andNeverFillsTheDecision_RN37_ADR15()
      throws Exception {
    var body =
        """
        {"kind": "CONFORMITY_DECISION", "propertyId": "%s"}\
        """
            .formatted(propertyId);
    var first = generate(body);
    var second = generate(body);
    call(HttpMethod.GET, "/documents/" + first, coordinator.accessToken(), null)
        .andExpect(jsonPath("$.status").value("SUPERSEDED"))
        .andExpect(jsonPath("$.supersededById").value(second));
    String text = pdfText(second);
    assertThat(text)
        .contains("( ) Conforme ( ) Conforme com recomendações ( ) Não conforme")
        .contains(
            "Não há visita de verificação registrada neste ciclo.") // RN38: aviso, não bloqueio
        .contains("José Visitado (fornecedor)", "Ana Perto", "Bruno Longe") // todos do grupo
        .contains("Só vale com as assinaturas")
        .doesNotContain("(X) Conforme");
  }

  @Test
  void visitRecord_ofAPlannedVisit_isBlocked_butTheVisitFormIsGenerated_RN38() throws Exception {
    var visitId = createVisit("PEER", false, near);
    call(
            HttpMethod.POST,
            "/documents",
            coordinator.accessToken(),
            """
            {"kind": "PEER_VISIT_RECORD", "visitId": "%s"}\
            """
                .formatted(visitId))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("DOCUMENT_PRECONDITION_FAILED"));
    var form =
        generate(
            """
            {"kind": "VISIT_FORM", "visitId": "%s"}\
            """
                .formatted(visitId));
    assertThat(pdfText(form))
        .contains("Roteiro de visita", "Sítio Boa Vista", "Ana Perto")
        .contains("Insumos utilizados constam dos Anexos da Portaria 52/2021");
  }

  @Test
  void everyTemplate_isGenerated() throws Exception {
    var visitId = createVisit("PEER", false, near);
    String certId =
        read(
            call(
                    HttpMethod.POST,
                    "/certifications",
                    coordinator.accessToken(),
                    """
                    {"propertyId": "%s", "number": "PM-77", "issuedOn": "%s",
                     "products": [{"productName": "Umbu"}]}\
                    """
                        .formatted(propertyId, today()))
                .andExpect(status().isCreated()),
            "$.id");
    generate(
        """
        {"kind": "MEMBERSHIP_REQUEST", "farmerId": "%s", "propertyId": "%s"}\
        """
            .formatted(visited.user().getId(), propertyId));
    generate(
        """
        {"kind": "EVALUATION_REQUEST", "groupId": "%s", "propertyIds": ["%s"]}\
        """
            .formatted(groupId, propertyId));
    var plan =
        generate(
            """
            {"kind": "VISIT_PLAN", "groupId": "%s", "from": "%s", "to": "%s"}\
            """
                .formatted(groupId, today(), today().plusDays(30)));
    assertThat(pdfText(plan)).contains("Visita de pares", "Sítio Boa Vista");
    var cert =
        generate(
            """
            {"kind": "CERTIFICATE", "certificationId": "%s"}\
            """
                .formatted(certId));
    assertThat(pdfText(cert)).contains("PM-77", "Umbu");
    assertThat(visitId).isNotNull();
  }

  @Test
  void verifyByCode_signedCopy_andCancel() throws Exception {
    var id =
        generate(
            """
            {"kind": "CONFORMITY_DECISION", "propertyId": "%s"}\
            """
                .formatted(propertyId));
    String code =
        read(
            call(HttpMethod.GET, "/documents/" + id, coordinator.accessToken(), null),
            "$.verificationCode");
    call(
            HttpMethod.GET,
            "/documents/verify/" + code.toLowerCase().replace("-", " "),
            coordinator.accessToken(),
            null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));

    byte[] pdf =
        call(HttpMethod.GET, "/documents/" + id + "/content", coordinator.accessToken(), null)
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    mockMvc
        .perform(
            MockMvcRequestBuilders.multipart("/documents/" + id + "/signed-copy")
                .file(new MockMultipartFile("file", "assinado.pdf", "application/pdf", pdf))
                .header("Authorization", "Bearer " + coordinator.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SIGNED"))
        .andExpect(jsonPath("$.signedAttachmentId").exists());

    call(
            HttpMethod.POST,
            "/documents/" + id + "/cancel",
            coordinator.accessToken(),
            """
            {"reason": "Gerado por engano"}\
            """)
        .andExpect(status().isNoContent());
    call(HttpMethod.GET, "/documents/" + id, coordinator.accessToken(), null)
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
  }

  @Test
  void farmer_cannotUseDocumentRoutes() throws Exception {
    call(HttpMethod.GET, "/documents", near.accessToken(), null).andExpect(status().isForbidden());
  }
}
