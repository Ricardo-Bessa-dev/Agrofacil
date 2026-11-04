// Ativação do aparelho do agricultor e rotação do refresh token (doc 1 §3.3.1, §3.3.2; RN03, RN04).
package br.edu.ifba.agrofacil.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.ifba.agrofacil.IntegrationTest;
import br.edu.ifba.agrofacil.support.TestData;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class FarmerAuthTest extends IntegrationTest {

  /** Cadastra um agricultor pelo painel e gera o código; devolve [farmerId, cpf, código]. */
  private String[] farmerWithCode(String coordinatorToken) throws Exception {
    String cpf = TestData.cpf();
    var created =
        call(
                HttpMethod.POST,
                "/farmers",
                coordinatorToken,
                """
                {"name": "José da Silva", "cpf": "%s", "phone": "+5574999998888"}\
                """
                    .formatted(cpf))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.hasPendingAccessCode").value(false));
    String farmerId = read(created, "$.id");
    var code =
        call(HttpMethod.POST, "/farmers/" + farmerId + "/access-codes", coordinatorToken, null)
            .andExpect(status().isCreated());
    return new String[] {farmerId, cpf, read(code, "$.code")};
  }

  private String activateBody(String cpf, String code, UUID deviceId) {
    return """
    {"cpf": "%s", "code": "%s",
     "device": {"id": "%s", "name": "Samsung A15", "platform": "ANDROID", "appVersion": "1.0.0"}}\
    """
        .formatted(cpf, code, deviceId);
  }

  @Test
  void activation_returnsSession_andRefreshRotates_RN03_RN04() throws Exception {
    var coordinator = fixtures.coordinator();
    var farmer = farmerWithCode(coordinator.accessToken());
    var deviceId = UUID.randomUUID();

    var session =
        call(
                HttpMethod.POST,
                "/auth/farmer/activate",
                null,
                activateBody(farmer[1], farmer[2], deviceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.role").value("FARMER"))
            .andExpect(jsonPath("$.accessTokenExpiresIn").value(900));
    String access = read(session, "$.accessToken");
    String refreshA = read(session, "$.refreshToken");

    call(HttpMethod.GET, "/me", access, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cpf").value(farmer[1]))
        .andExpect(jsonPath("$.association.tarefaM2").value(4356));

    // O código é de uso único.
    call(
            HttpMethod.POST,
            "/auth/farmer/activate",
            null,
            activateBody(farmer[1], farmer[2], deviceId))
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_CODE"));

    String refreshBody = "{\"refreshToken\": \"%s\", \"deviceId\": \"%s\"}";
    var rotated =
        call(HttpMethod.POST, "/auth/refresh", null, refreshBody.formatted(refreshA, deviceId))
            .andExpect(status().isOk());
    String refreshB = read(rotated, "$.refreshToken");

    // Reutilizar o token antigo depois da tolerância revoga a família inteira.
    clock.advance(Duration.ofSeconds(31));
    call(HttpMethod.POST, "/auth/refresh", null, refreshBody.formatted(refreshA, deviceId))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
    call(HttpMethod.POST, "/auth/refresh", null, refreshBody.formatted(refreshB, deviceId))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
  }

  @Test
  void lostResponse_withinGraceWindow_doesNotKillTheSession() throws Exception {
    var coordinator = fixtures.coordinator();
    var codes = farmerWithCode(coordinator.accessToken());
    var deviceId = UUID.randomUUID();
    String refreshA =
        read(
            call(
                HttpMethod.POST,
                "/auth/farmer/activate",
                null,
                activateBody(codes[1], codes[2], deviceId)),
            "$.refreshToken");
    String body = "{\"refreshToken\": \"%s\", \"deviceId\": \"%s\"}".formatted(refreshA, deviceId);
    call(HttpMethod.POST, "/auth/refresh", null, body).andExpect(status().isOk());
    clock.advance(Duration.ofSeconds(5));
    call(HttpMethod.POST, "/auth/refresh", null, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.refreshToken").isString());
  }

  @Test
  void fiveWrongCodes_lockTheCode_evenForTheRightOne_RN03() throws Exception {
    var coordinator = fixtures.coordinator();
    var farmer = farmerWithCode(coordinator.accessToken());
    String wrong = farmer[2].equals("000000") ? "111111" : "000000";
    for (int i = 1; i <= 4; i++) {
      call(
              HttpMethod.POST,
              "/auth/farmer/activate",
              null,
              activateBody(farmer[1], wrong, UUID.randomUUID()))
          .andExpect(jsonPath("$.code").value("INVALID_ACCESS_CODE"));
    }
    call(
            HttpMethod.POST,
            "/auth/farmer/activate",
            null,
            activateBody(farmer[1], wrong, UUID.randomUUID()))
        .andExpect(jsonPath("$.code").value("ACCESS_CODE_LOCKED"));
    rateLimiter.reset(); // o limite por CPF (5/h) também barraria a 6ª tentativa
    call(
            HttpMethod.POST,
            "/auth/farmer/activate",
            null,
            activateBody(farmer[1], farmer[2], UUID.randomUUID()))
        .andExpect(jsonPath("$.code").value("ACCESS_CODE_LOCKED"));
  }

  @Test
  void unknownCpf_getsTheSameAnswerAsWrongCode() throws Exception {
    call(
            HttpMethod.POST,
            "/auth/farmer/activate",
            null,
            activateBody(TestData.cpf(), "123456", UUID.randomUUID()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_CODE"));
  }

  @Test
  void revokedDevice_cannotRenewTheSession_RN04() throws Exception {
    var coordinator = fixtures.coordinator();
    var farmer = farmerWithCode(coordinator.accessToken());
    var deviceId = UUID.randomUUID();
    String refresh =
        read(
            call(
                HttpMethod.POST,
                "/auth/farmer/activate",
                null,
                activateBody(farmer[1], farmer[2], deviceId)),
            "$.refreshToken");
    call(HttpMethod.DELETE, "/devices/" + deviceId, coordinator.accessToken(), null)
        .andExpect(status().isNoContent());
    call(
            HttpMethod.POST,
            "/auth/refresh",
            null,
            "{\"refreshToken\": \"%s\", \"deviceId\": \"%s\"}".formatted(refresh, deviceId))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("DEVICE_REVOKED"));
  }

  @Test
  void deactivatedFarmer_cannotActivate_RN25() throws Exception {
    var coordinator = fixtures.coordinator();
    var farmer = farmerWithCode(coordinator.accessToken());
    call(HttpMethod.POST, "/farmers/" + farmer[0] + "/deactivate", coordinator.accessToken(), null)
        .andExpect(status().isNoContent());
    call(
            HttpMethod.POST,
            "/auth/farmer/activate",
            null,
            activateBody(farmer[1], farmer[2], UUID.randomUUID()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_INACTIVE"));
  }
}
