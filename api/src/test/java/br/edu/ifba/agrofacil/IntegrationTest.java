// Base dos testes de integração: contexto completo, MockMvc e Postgres real (Testcontainers).
package br.edu.ifba.agrofacil;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import br.edu.ifba.agrofacil.common.security.RateLimiter;
import br.edu.ifba.agrofacil.support.CapturingMailGateway;
import br.edu.ifba.agrofacil.support.Fixtures;
import br.edu.ifba.agrofacil.support.MutableClock;
import br.edu.ifba.agrofacil.support.TestDoubles;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Estenda esta classe nos testes que precisam da API inteira e do banco. O banco é compartilhado
 * entre os testes: cada teste cria os próprios dados (CPF e e-mail aleatórios).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestDoubles.class})
public abstract class IntegrationTest {

  @Autowired protected MockMvc mockMvc;
  @Autowired protected Fixtures fixtures;
  @Autowired protected MutableClock clock;
  @Autowired protected CapturingMailGateway mail;
  @Autowired protected RateLimiter rateLimiter;

  @BeforeEach
  void resetSharedState() {
    clock.reset();
    rateLimiter.reset();
  }

  /** Requisição JSON autenticada; {@code token} nulo = sem login. */
  protected ResultActions call(HttpMethod method, String path, String token, String json)
      throws Exception {
    MockHttpServletRequestBuilder req = request(method, path);
    if (token != null) {
      req.header("Authorization", "Bearer " + token);
    }
    if (json != null) {
      req.contentType(MediaType.APPLICATION_JSON).content(json);
    }
    return mockMvc.perform(req);
  }

  /** Lê um valor do corpo JSON da resposta. */
  protected static <T> T read(ResultActions result, String jsonPath) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath);
  }
}
