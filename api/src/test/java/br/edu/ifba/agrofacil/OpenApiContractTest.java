// Confere que o OpenAPI é gerado com as uniões do contrato e salva uma cópia em
// target/openapi.json.
package br.edu.ifba.agrofacil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** O {@code pnpm gen:api} lê o mesmo documento da API rodando (doc 2 §1.16). */
class OpenApiContractTest extends IntegrationTest {

  @Test
  void openApi_describesTheRecordUnions_andTheMainRoutes() throws Exception {
    String json =
        mockMvc
            .perform(get("/openapi.json"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    Files.writeString(Path.of("target/openapi.json"), json);

    Map<String, Object> paths = read(json, "$.paths");
    assertThat(paths)
        .containsKeys("/sync/push", "/sync/pull", "/records", "/farmers", "/auth/farmer/activate")
        .containsKeys(
            "/certifications", "/announcements", "/dashboard/summary", "/reports/field-notebook")
        .containsKeys(
            "/visits", "/visits/suggest-visitors", "/documents", "/catalog/visit-checklist");
    Map<String, Object> schemas = read(json, "$.components.schemas");
    assertThat(schemas).containsKeys("PlantingWrite", "HarvestRecord", "Me", "PullResponse");
    assertThat((Object) read(json, "$.components.schemas.PlantingRecord.properties.performedOn"))
        .as("os campos comuns do registro aparecem no esquema de cada tipo")
        .isNotNull();
  }

  private static <T> T read(String json, String path) {
    return com.jayway.jsonpath.JsonPath.read(json, path);
  }
}
