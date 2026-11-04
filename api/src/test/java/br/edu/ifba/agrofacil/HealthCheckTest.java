// Confere que a API sobe com o banco, que o health check é público e que o resto exige login.
package br.edu.ifba.agrofacil;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class HealthCheckTest extends IntegrationTest {

  @Test
  void healthCheck_isPublicAndUp() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void otherRoutes_withoutToken_returnUnauthorized() throws Exception {
    mockMvc.perform(get("/me")).andExpect(status().isUnauthorized());
  }
}
