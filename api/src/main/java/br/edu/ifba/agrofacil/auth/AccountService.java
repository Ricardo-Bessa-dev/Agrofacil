// Convite de coordenadores, aceite do convite e redefinição de senha (doc 1 §3.3.5, §3.7.2).
package br.edu.ifba.agrofacil.auth;

import br.edu.ifba.agrofacil.common.domain.PasswordPolicy;
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
public class AccountService {

  private static final Logger log = LoggerFactory.getLogger(AccountService.class);
  private static final Duration INVITE_TTL = Duration.ofDays(7);
  private static final Duration RESET_TTL = Duration.ofMinutes(30);
  private static final int MAX_FORGOT_PER_EMAIL_PER_HOUR = 3;

  private final UserRepository users;
  private final UserTokenRepository userTokens;
  private final TokenService tokens;
  private final PasswordEncoder passwordEncoder;
  private final MailService mail;
  private final MailComposer composer;
  private final RateLimiter rateLimiter;
  private final Clock clock;

  public AccountService(
      UserRepository users,
      UserTokenRepository userTokens,
      TokenService tokens,
      PasswordEncoder passwordEncoder,
      MailService mail,
      MailComposer composer,
      RateLimiter rateLimiter,
      Clock clock) {
    this.users = users;
    this.userTokens = userTokens;
    this.tokens = tokens;
    this.passwordEncoder = passwordEncoder;
    this.mail = mail;
    this.composer = composer;
    this.rateLimiter = rateLimiter;
    this.clock = clock;
  }

  /**
   * Cria o coordenador (inativo até aceitar) e envia o convite por e-mail.
   *
   * @throws ApiException 409 EMAIL_IN_USE
   */
  @Transactional
  public AppUser invite(UUID associationId, String name, String email) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    if (users.findByEmailIgnoreCase(normalized).isPresent()) {
      throw new ApiException(ErrorCode.EMAIL_IN_USE);
    }
    var user = users.save(AppUser.newInvitedCoordinator(associationId, name.trim(), normalized));
    String token = newUserToken(user.getId(), UserToken.Purpose.INVITE, INVITE_TTL);
    mail.sendAfterCommit(composer.invite(normalized, token));
    log.info("Convite enviado para o coordenador {}", user.getId());
    return user;
  }

  /** Aceita o convite: define nome e senha e ativa o coordenador. */
  @Transactional
  public void acceptInvite(AuthDtos.AcceptInviteRequest req) {
    var token = usableToken(req.token(), UserToken.Purpose.INVITE);
    ensureStrong(req.password());
    var user =
        users
            .findById(token.getUserId())
            .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
    user.acceptInvite(req.name().trim(), passwordEncoder.encode(req.password()));
    token.markUsed(clock.instant());
    log.info("Convite aceito pelo coordenador {}", user.getId());
  }

  /** Sempre responde igual; só envia o link se o e-mail for de um coordenador ativo. */
  @Transactional
  public void forgotPassword(String email) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    rateLimiter.check("forgot:" + normalized, MAX_FORGOT_PER_EMAIL_PER_HOUR, Duration.ofHours(1));
    users
        .findByEmailIgnoreCase(normalized)
        .filter(u -> u.getRole() == Role.COORDINATOR && u.isActive())
        .ifPresent(u -> {
          String token = newUserToken(u.getId(), UserToken.Purpose.PASSWORD_RESET, RESET_TTL);
          mail.sendAfterCommit(composer.passwordReset(u.getEmail(), token));
        });
  }

  /** Troca a senha pelo link do e-mail e derruba todas as sessões do coordenador. */
  @Transactional
  public void resetPassword(AuthDtos.ResetPasswordRequest req) {
    var token = usableToken(req.token(), UserToken.Purpose.PASSWORD_RESET);
    ensureStrong(req.newPassword());
    var user =
        users
            .findById(token.getUserId())
            .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
    user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
    token.markUsed(clock.instant());
    tokens.revokeAllForUser(user.getId());
    log.info("Senha redefinida para o coordenador {}", user.getId());
  }

  /** Cria o token de uso único e devolve o valor em texto (só ele vai no e-mail). */
  @Transactional
  public String newUserToken(UUID userId, UserToken.Purpose purpose, Duration ttl) {
    Instant now = clock.instant();
    String raw = SecureTokens.newToken();
    userTokens.save(
        new UserToken(userId, purpose, SecureTokens.sha256Hex(raw), now.plus(ttl), now));
    return raw;
  }

  private UserToken usableToken(String raw, UserToken.Purpose purpose) {
    return userTokens
        .findByTokenHashAndPurpose(SecureTokens.sha256Hex(raw), purpose)
        .filter(t -> t.isUsable(clock.instant()))
        .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
  }

  private static void ensureStrong(String password) {
    if (!PasswordPolicy.isStrong(password)) {
      throw new ApiException(ErrorCode.WEAK_PASSWORD);
    }
  }
}
