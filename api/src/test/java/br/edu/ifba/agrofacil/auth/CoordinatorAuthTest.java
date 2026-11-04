// Login do coordenador com 2FA, renovação por cookie, convite e senha (doc 1 §3.3.4, §3.3.5).
package br.edu.ifba.agrofacil.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.ifba.agrofacil.IntegrationTest;
import br.edu.ifba.agrofacil.support.Fixtures;
import br.edu.ifba.agrofacil.support.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CoordinatorAuthTest extends IntegrationTest {

  @Test
  void loginWith2fa_opensWebSession_andCookieRenewsIt_RN05() throws Exception {
    var coordinator = fixtures.coordinator();
    String email = coordinator.user().getEmail();

    var login =
        call(
                HttpMethod.POST,
                "/auth/coordinator/login",
                null,
                """
                {"email": "%s", "password": "%s"}\
                """
                    .formatted(email, Fixtures.PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresIn").value(600));
    String challengeId = read(login, "$.challengeId");
    String code = mail.extract(email, "(\\d{6})");

    var verify =
        call(
                HttpMethod.POST,
                "/auth/coordinator/verify",
                null,
                """
                {"challengeId": "%s", "code": "%s"}\
                """
                    .formatted(challengeId, code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.role").value("COORDINATOR"))
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(cookie().httpOnly("af_refresh", true));
    String accessToken = read(verify, "$.accessToken");
    var refreshCookie = verify.andReturn().getResponse().getCookie("af_refresh");

    call(HttpMethod.GET, "/me", accessToken, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email));

    // Sem o cabeçalho anti-CSRF, o cookie sozinho não renova.
    mockMvc
        .perform(post("/auth/refresh").cookie(new Cookie("af_refresh", refreshCookie.getValue())))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/auth/refresh")
                .header("X-Requested-With", "agrofacil-web")
                .cookie(new Cookie("af_refresh", refreshCookie.getValue())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(cookie().exists("af_refresh"));
  }

  @Test
  void wrongPassword_andUnknownEmail_getTheSameAnswer() throws Exception {
    var coordinator = fixtures.coordinator();
    call(
            HttpMethod.POST,
            "/auth/coordinator/login",
            null,
            """
            {"email": "%s", "password": "senha-errada-123"}\
            """
                .formatted(coordinator.user().getEmail()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    call(
            HttpMethod.POST,
            "/auth/coordinator/login",
            null,
            """
            {"email": "ninguem@teste.org", "password": "senha-errada-123"}\
            """)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void fiveWrong2faCodes_lockTheChallenge_RN05() throws Exception {
    var coordinator = fixtures.coordinator();
    var login =
        call(
            HttpMethod.POST,
            "/auth/coordinator/login",
            null,
            """
            {"email": "%s", "password": "%s"}\
            """
                .formatted(coordinator.user().getEmail(), Fixtures.PASSWORD));
    String challengeId = read(login, "$.challengeId");
    String code = mail.extract(coordinator.user().getEmail(), "(\\d{6})");
    String wrong = code.equals("000000") ? "111111" : "000000";
    String body = "{\"challengeId\": \"%s\", \"code\": \"%s\"}";
    for (int i = 1; i <= 4; i++) {
      call(HttpMethod.POST, "/auth/coordinator/verify", null, body.formatted(challengeId, wrong))
          .andExpect(jsonPath("$.code").value("INVALID_2FA_CODE"));
    }
    call(HttpMethod.POST, "/auth/coordinator/verify", null, body.formatted(challengeId, wrong))
        .andExpect(jsonPath("$.code").value("LOCKED_2FA_CODE"));
    call(HttpMethod.POST, "/auth/coordinator/verify", null, body.formatted(challengeId, code))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("LOCKED_2FA_CODE"));
  }

  @Test
  void invitedCoordinator_acceptsInvite_andCanLogIn_ADR04() throws Exception {
    var inviter = fixtures.coordinator();
    String email = TestData.email();
    call(
            HttpMethod.POST,
            "/coordinators/invitations",
            inviter.accessToken(),
            """
            {"name": "Nova Coordenadora", "email": "%s"}\
            """
                .formatted(email))
        .andExpect(status().isAccepted());
    String token = mail.extract(email, "convite\\?token=([\\w-]+)");

    call(
            HttpMethod.POST,
            "/auth/invite/accept",
            null,
            """
            {"token": "%s", "name": "Maria", "password": "123"}\
            """
                .formatted(token))
        .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    call(
            HttpMethod.POST,
            "/auth/invite/accept",
            null,
            """
            {"token": "%s", "name": "Maria", "password": "umbuzeiro-florido-2026"}\
            """
                .formatted(token))
        .andExpect(status().isNoContent());

    call(
            HttpMethod.POST,
            "/auth/coordinator/login",
            null,
            """
            {"email": "%s", "password": "umbuzeiro-florido-2026"}\
            """
                .formatted(email))
        .andExpect(status().isOk());
    // O link é de uso único.
    call(
            HttpMethod.POST,
            "/auth/invite/accept",
            null,
            """
            {"token": "%s", "name": "Maria", "password": "umbuzeiro-florido-2026"}\
            """
                .formatted(token))
        .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
  }

  @Test
  void forgotPassword_alwaysAnswers202_andResetChangesThePassword() throws Exception {
    var coordinator = fixtures.coordinator();
    String email = coordinator.user().getEmail();
    call(HttpMethod.POST, "/auth/password/forgot", null, "{\"email\": \"ninguem@teste.org\"}")
        .andExpect(status().isAccepted());
    call(HttpMethod.POST, "/auth/password/forgot", null, "{\"email\": \"%s\"}".formatted(email))
        .andExpect(status().isAccepted());
    String token = mail.extract(email, "redefinir-senha\\?token=([\\w-]+)");
    call(
            HttpMethod.POST,
            "/auth/password/reset",
            null,
            """
            {"token": "%s", "newPassword": "mandacaru-no-sertao"}\
            """
                .formatted(token))
        .andExpect(status().isNoContent());
    call(
            HttpMethod.POST,
            "/auth/coordinator/login",
            null,
            """
            {"email": "%s", "password": "mandacaru-no-sertao"}\
            """
                .formatted(email))
        .andExpect(status().isOk());
  }

  @Test
  void lastActiveCoordinator_cannotBeDeactivated_RN24() throws Exception {
    var coordinator = fixtures.coordinator();
    // Desativa todos os outros coordenadores ativos criados por outros testes.
    var list = call(HttpMethod.GET, "/coordinators", coordinator.accessToken(), null);
    java.util.List<String> others = read(list, "$[?(@.status == 'ACTIVE')].id");
    others.remove(coordinator.user().getId().toString());
    for (String id : others) {
      call(
              HttpMethod.PATCH,
              "/coordinators/" + id,
              coordinator.accessToken(),
              "{\"status\":\"INACTIVE\"}")
          .andExpect(status().isOk());
    }
    call(
            HttpMethod.PATCH,
            "/coordinators/" + coordinator.user().getId(),
            coordinator.accessToken(),
            "{\"status\":\"INACTIVE\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LAST_COORDINATOR"));
    assertThat(others).doesNotContain(coordinator.user().getId().toString());
  }
}
