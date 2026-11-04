// Rotas públicas de autenticação do app e do painel (doc 1 §3.3).
package br.edu.ifba.agrofacil.auth;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.config.AppProperties;
import br.edu.ifba.agrofacil.user.DeviceService;
import br.edu.ifba.agrofacil.user.MeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * No web, o refresh token vive no cookie {@code af_refresh} (HttpOnly, SameSite=Strict) e as rotas
 * que o usam exigem {@code X-Requested-With: agrofacil-web} contra CSRF (doc 1 §3.2.6).
 */
@Tag(name = "Autenticação")
@RestController
@RequestMapping("/auth")
public class AuthController {

  static final String REFRESH_COOKIE = "af_refresh";
  static final String COOKIE_PATH = "/api/v1/auth";
  static final String CSRF_HEADER = "X-Requested-With";
  static final String CSRF_VALUE = "agrofacil-web";

  private final FarmerActivationService activation;
  private final CoordinatorLoginService coordinatorLogin;
  private final AccountService accounts;
  private final TokenService tokens;
  private final DeviceService devices;
  private final MeService me;
  private final AppProperties props;
  private final Clock clock;

  public AuthController(
      FarmerActivationService activation,
      CoordinatorLoginService coordinatorLogin,
      AccountService accounts,
      TokenService tokens,
      DeviceService devices,
      MeService me,
      AppProperties props,
      Clock clock) {
    this.activation = activation;
    this.coordinatorLogin = coordinatorLogin;
    this.accounts = accounts;
    this.tokens = tokens;
    this.devices = devices;
    this.me = me;
    this.props = props;
    this.clock = clock;
  }

  @Operation(summary = "Agricultor: ativa o aparelho com CPF + código de acesso")
  @PostMapping("/farmer/activate")
  public AuthDtos.SessionResponse activate(
      @Valid @RequestBody AuthDtos.ActivateRequest body, HttpServletRequest request) {
    var session = activation.activate(body, request.getRemoteAddr());
    return new AuthDtos.SessionResponse(
        session.accessToken(),
        session.accessTokenExpiresIn(),
        session.refreshToken(),
        me.build(session.user()));
  }

  @Operation(summary = "Renova a sessão (mobile: corpo; web: cookie + X-Requested-With)")
  @PostMapping("/refresh")
  public ResponseEntity<AuthDtos.SessionResponse> refresh(
      @RequestBody(required = false) AuthDtos.RefreshRequest body,
      @CookieValue(name = REFRESH_COOKIE, required = false) String cookie,
      @RequestHeader(name = CSRF_HEADER, required = false) String csrf) {
    if (body != null && body.refreshToken() != null) {
      if (body.deviceId() == null) {
        throw ApiException.validation("deviceId", "REQUIRED", "Informe o aparelho.");
      }
      var session = tokens.refresh(body.refreshToken(), body.deviceId());
      return ResponseEntity.ok(
          new AuthDtos.SessionResponse(
              session.accessToken(), session.accessTokenExpiresIn(), session.refreshToken(), null));
    }
    requireWebCsrfHeader(csrf);
    if (cookie == null) {
      throw new ApiException(ErrorCode.REFRESH_INVALID);
    }
    var session = tokens.refresh(cookie, null);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie(session).toString())
        .body(
            new AuthDtos.SessionResponse(
                session.accessToken(), session.accessTokenExpiresIn(), null, null));
  }

  @Operation(summary = "Sai: no app revoga o aparelho; no web revoga a sessão do cookie")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @RequestBody(required = false) AuthDtos.LogoutRequest body,
      @CookieValue(name = REFRESH_COOKIE, required = false) String cookie,
      @RequestHeader(name = CSRF_HEADER, required = false) String csrf) {
    if (body != null && body.deviceId() != null) {
      // O app manda o token de acesso: só o dono do aparelho pode desconectá-lo.
      var user =
          CurrentUserProvider.currentOrNull()
              .filter(u -> body.deviceId().equals(u.deviceId()))
              .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
      devices.logout(user, body.deviceId());
      return ResponseEntity.noContent().build();
    }
    requireWebCsrfHeader(csrf);
    if (cookie != null) {
      tokens.revokeByToken(cookie);
    }
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
        .build();
  }

  @Operation(summary = "Coordenador: 1ª etapa do login (envia o código por e-mail)")
  @PostMapping("/coordinator/login")
  public AuthDtos.LoginChallengeResponse login(
      @Valid @RequestBody AuthDtos.LoginRequest body, HttpServletRequest request) {
    return coordinatorLogin.login(body, request.getRemoteAddr());
  }

  @Operation(summary = "Coordenador: 2ª etapa do login (confere o código)")
  @PostMapping("/coordinator/verify")
  public ResponseEntity<AuthDtos.SessionResponse> verify(
      @Valid @RequestBody AuthDtos.VerifyRequest body) {
    var session = coordinatorLogin.verify(body);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie(session).toString())
        .body(
            new AuthDtos.SessionResponse(
                session.accessToken(),
                session.accessTokenExpiresIn(),
                null,
                me.build(session.user())));
  }

  @PostMapping("/coordinator/resend")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resend(@Valid @RequestBody AuthDtos.ResendRequest body) {
    coordinatorLogin.resend(body.challengeId());
  }

  @Operation(summary = "Sempre 202; envia o link só se o e-mail existir")
  @PostMapping("/password/forgot")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void forgot(@Valid @RequestBody AuthDtos.ForgotPasswordRequest body) {
    accounts.forgotPassword(body.email());
  }

  @PostMapping("/password/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reset(@Valid @RequestBody AuthDtos.ResetPasswordRequest body) {
    accounts.resetPassword(body);
  }

  @PostMapping("/invite/accept")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void acceptInvite(@Valid @RequestBody AuthDtos.AcceptInviteRequest body) {
    accounts.acceptInvite(body);
  }

  private static void requireWebCsrfHeader(String csrf) {
    if (!CSRF_VALUE.equals(csrf)) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
  }

  private ResponseCookie refreshCookie(TokenService.Session session) {
    long seconds =
        Math.max(0, Duration.between(clock.instant(), session.refreshExpiresAt()).toSeconds());
    return baseCookie(session.refreshToken()).maxAge(seconds).build();
  }

  private ResponseCookie clearedCookie() {
    return baseCookie("").maxAge(0).build();
  }

  private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(props.secureCookies())
        .sameSite("Strict")
        .path(COOKIE_PATH);
  }
}
