// Testes obrigatórios de segurança: papéis, escopo (IDOR) e formato dos erros (doc 2 §1.17).
package br.edu.ifba.agrofacil.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.ifba.agrofacil.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

class SecurityScopeTest extends IntegrationTest {

  @Test
  void withoutToken_returnsProblemDetails401() throws Exception {
    call(HttpMethod.GET, "/me", null, null)
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.traceId").isString())
        .andExpect(header().exists("X-Trace-Id"));
  }

  @Test
  void farmer_cannotUseCoordinatorRoutes() throws Exception {
    var farmer = fixtures.farmer("Pedro");
    for (String path : new String[] {"/farmers", "/records", "/association", "/coordinators"}) {
      call(HttpMethod.GET, path, farmer.accessToken(), null)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
  }

  @Test
  void coordinator_cannotUseFarmerSync() throws Exception {
    var coordinator = fixtures.coordinator();
    call(HttpMethod.GET, "/sync/pull?cursor=0", coordinator.accessToken(), null)
        .andExpect(status().isForbidden());
  }

  @Test
  void oldAppVersion_getsUpgradeRequired() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/me")
                .header("X-App-Version", "0.9.0"))
        .andExpect(status().isUpgradeRequired())
        .andExpect(jsonPath("$.code").value("APP_UPDATE_REQUIRED"));
  }

  @Test
  void invalidBody_returns422WithFieldErrorsInPortuguese() throws Exception {
    var coordinator = fixtures.coordinator();
    call(
            HttpMethod.POST,
            "/farmers",
            coordinator.accessToken(),
            "{\"name\": \"\", \"cpf\": \"123\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("name"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"))
        .andExpect(jsonPath("$.errors[0].message").value("Este campo precisa ser preenchido."));
    call(
            HttpMethod.POST,
            "/farmers",
            coordinator.accessToken(),
            "{\"name\": \"Zé\", \"cpf\": \"123\"}")
        .andExpect(jsonPath("$.code").value("INVALID_CPF"));
  }
}
