// Login do coordenador em duas etapas: senha e depois código por e-mail (ADR-04, RN05).
package br.edu.ifba.agrofacil.auth;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.RateLimiter;
import br.edu.ifba.agrofacil.common.security.Role;
import br.edu.ifba.agrofacil.common.security.SecureTokens;
import br.edu.ifba.agrofacil.mail.MailComposer;
import br.edu.ifba.agrofacil.mail.MailService;
import br.edu.ifba.agrofacil.user.AppUser;
import br.edu.ifba.agrofacil.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoordinatorLoginService {

  private static final Logger log = LoggerFactory.getLogger(CoordinatorLoginService.class);
  private static final Duration CODE_TTL = Duration.ofMinutes(10); // RN05
  private static final int MAX_PER_EMAIL = 5; // a cada 15 min
  private static final int MAX_PER_IP_PER_HOUR = 20;
  private static final int MAX_RESENDS = 3; // por desafio
  private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);

  private final UserRepository users;
  private final LoginChallengeRepository challenges;
  private final TokenService tokens;
  private final PasswordEncoder passwordEncoder;
  private final RateLimiter rateLimiter;
  private final MailService mail;
  private final MailComposer composer;
  private final Clock clock;

  /** Hash falso para o tempo de resposta ser igual com e sem usuário (doc 2 §1.7.5). */
  private final String dummyHash;

  public CoordinatorLoginService(
      UserRepository users,
      LoginChallengeRepository challenges,
      TokenService tokens,
      PasswordEncoder passwordEncoder,
      RateLimiter rateLimiter,
      MailService mail,
      MailComposer composer,
      Clock clock) {
    this.users = users;
    this.challenges = challenges;
    this.tokens = tokens;
    this.passwordEncoder = passwordEncoder;
    this.rateLimiter = rateLimiter;
    this.mail = mail;
    this.composer = composer;
    this.clock = clock;
    this.dummyHash = passwordEncoder.encode(SecureTokens.newToken());
  }

  /**
   * Primeira etapa: confere a senha e envia o código por e-mail.
   *
   * @throws ApiException 401 INVALID_CREDENTIALS, a mesma para e-mail inexistente e senha errada
   */
  @Transactional
  public AuthDtos.LoginChallengeResponse login(AuthDtos.LoginRequest req, String clientIp) {
    String email = req.email().trim().toLowerCase(Locale.ROOT);
    rateLimiter.check("login:email:" + email, MAX_PER_EMAIL, Duration.ofMinutes(15));
    rateLimiter.check("login:ip:" + clientIp, MAX_PER_IP_PER_HOUR, Duration.ofHours(1));

    var user = users.findByEmailIgnoreCase(email).filter(u -> u.getRole() == Role.COORDINATOR);
    String hash = user.map(AppUser::getPasswordHash).orElse(null);
    boolean matches = passwordEncoder.matches(req.password(), hash != null ? hash : dummyHash);
    if (user.isEmpty() || hash == null || !matches || !user.get().isActive()) {
      log.info("Login de coordenador recusado");
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
    }

    Instant now = clock.instant();
    String code = SecureTokens.newSixDigitCode();
    var challenge =
        challenges.save(
            new LoginChallenge(
                user.get().getId(), passwordEncoder.encode(code), now.plus(CODE_TTL), now));
    mail.sendAfterCommit(composer.twoFactorCode(user.get().getEmail(), code));
    return new AuthDtos.LoginChallengeResponse(
        challenge.getId(), emailHint(user.get().getEmail()), CODE_TTL.toSeconds());
  }

  /**
   * Segunda etapa: confere o código e abre a sessão web.
   *
   * @throws ApiException INVALID_2FA_CODE, EXPIRED_2FA_CODE ou LOCKED_2FA_CODE
   */
  @Transactional(noRollbackFor = ApiException.class)
  public TokenService.Session verify(AuthDtos.VerifyRequest req) {
    Instant now = clock.instant();
    var challenge =
        challenges
            .findById(req.challengeId())
            .filter(c -> !c.isUsed())
            .orElseThrow(() -> new ApiException(ErrorCode.INVALID_2FA_CODE));
    if (challenge.isLocked()) {
      throw new ApiException(ErrorCode.LOCKED_2FA_CODE);
    }
    if (challenge.isExpired(now)) {
      throw new ApiException(ErrorCode.EXPIRED_2FA_CODE);
    }
    if (!passwordEncoder.matches(req.code(), challenge.getCodeHash())) {
      challenge.registerFailedAttempt();
      throw new ApiException(
          challenge.isLocked() ? ErrorCode.LOCKED_2FA_CODE : ErrorCode.INVALID_2FA_CODE);
    }
    var user =
        users
            .findById(challenge.getUserId())
            .filter(u -> u.isActive() && !u.isDeleted())
            .orElseThrow(() -> new ApiException(ErrorCode.INVALID_2FA_CODE));
    challenge.markUsed(now);
    user.setLastLoginAt(now);
    log.info("Coordenador {} entrou no painel", user.getId());
    return tokens.startWebSession(user);
  }

  /** Gera e envia um novo código: no máximo 1 vez a cada 60 s e 3 vezes por desafio. */
  @Transactional
  public void resend(UUID challengeId) {
    rateLimiter.check("resend:interval:" + challengeId, 1, RESEND_INTERVAL);
    rateLimiter.check("resend:total:" + challengeId, MAX_RESENDS, CODE_TTL);
    Instant now = clock.instant();
    var challenge =
        challenges
            .findById(challengeId)
            .filter(c -> !c.isUsed() && !c.isLocked())
            .orElseThrow(() -> new ApiException(ErrorCode.INVALID_2FA_CODE));
    var user = users.findById(challenge.getUserId()).orElseThrow(ApiException::notFound);
    String code = SecureTokens.newSixDigitCode();
    challenge.replaceCode(passwordEncoder.encode(code), now.plus(CODE_TTL));
    mail.sendAfterCommit(composer.twoFactorCode(user.getEmail(), code));
  }

  /** {@code fulano@exemplo.com} → {@code fu***@exemplo.com}. */
  static String emailHint(String email) {
    int at = email.indexOf('@');
    if (at <= 0) {
      return "***";
    }
    String visible = email.substring(0, Math.min(2, at));
    return visible + "***" + email.substring(at);
  }
}
