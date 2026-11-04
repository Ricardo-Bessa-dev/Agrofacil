// Corpos de requisição e resposta das rotas /auth (doc 1 §3.3).
package br.edu.ifba.agrofacil.auth;

import br.edu.ifba.agrofacil.user.DeviceInfo;
import br.edu.ifba.agrofacil.user.Me;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Agrupados num arquivo só porque são pequenos e só existem para o {@link AuthController}. */
public final class AuthDtos {

  private AuthDtos() {}

  /**
   * @param cpf 11 dígitos; o app tira a máscara
   * @param code 6 dígitos
   */
  public record ActivateRequest(
      @NotBlank String cpf,
      @NotBlank @Pattern(regexp = "\\d{6}") String code,
      @NotNull @Valid DeviceInfo device) {}

  /** Resposta do login: no web, o refresh vai no cookie e {@code refreshToken} sai nulo. */
  public record SessionResponse(
      String accessToken,
      long accessTokenExpiresIn,
      @JsonInclude(JsonInclude.Include.NON_NULL) String refreshToken,
      @JsonInclude(JsonInclude.Include.NON_NULL) Me user) {}

  /** No mobile vem no corpo; no web o corpo é vazio e o token vem no cookie. */
  public record RefreshRequest(String refreshToken, UUID deviceId) {}

  public record LogoutRequest(UUID deviceId) {}

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  /**
   * @param emailHint e-mail parcialmente escondido, ex.: {@code ri***@gmail.com}
   * @param expiresIn segundos até o código vencer
   */
  public record LoginChallengeResponse(UUID challengeId, String emailHint, long expiresIn) {}

  public record VerifyRequest(
      @NotNull UUID challengeId, @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

  public record ResendRequest(@NotNull UUID challengeId) {}

  public record ForgotPasswordRequest(@NotBlank @Email String email) {}

  public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {}

  public record AcceptInviteRequest(
      @NotBlank String token, @NotBlank @Size(max = 120) String name, @NotBlank String password) {}
}
