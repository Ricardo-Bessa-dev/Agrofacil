// Emite o JWT de acesso e cuida dos refresh tokens: rotação, reutilização e revogação (RN04, RN05).
package br.edu.ifba.agrofacil.auth;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.common.security.SecureTokens;
import br.edu.ifba.agrofacil.config.AppProperties;
import br.edu.ifba.agrofacil.user.AppUser;
import br.edu.ifba.agrofacil.user.DeviceRepository;
import br.edu.ifba.agrofacil.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

  private static final Logger log = LoggerFactory.getLogger(TokenService.class);

  /**
   * Se o app enviar o refresh e perder a resposta, a próxima tentativa parece reutilização. Dentro
   * desta janela, do mesmo aparelho, ela é aceita (doc 2 §1.7.4).
   */
  private static final Duration REUSE_GRACE = Duration.ofSeconds(30);

  private final JwtEncoder jwtEncoder;
  private final RefreshTokenRepository refreshTokens;
  private final UserRepository users;
  private final DeviceRepository devices;
  private final AppProperties props;
  private final Clock clock;

  public TokenService(
      JwtEncoder jwtEncoder,
      RefreshTokenRepository refreshTokens,
      UserRepository users,
      DeviceRepository devices,
      AppProperties props,
      Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.refreshTokens = refreshTokens;
    this.users = users;
    this.devices = devices;
    this.props = props;
    this.clock = clock;
  }

  /**
   * Tokens de uma sessão.
   *
   * @param refreshExpiresAt usado como validade do cookie no web
   */
  public record Session(
      AppUser user,
      String accessToken,
      long accessTokenExpiresIn,
      String refreshToken,
      Instant refreshExpiresAt) {}

  /** Sessão nova do agricultor, presa ao aparelho (família nova de refresh tokens). */
  @Transactional
  public Session startFarmerSession(AppUser farmer, UUID deviceId) {
    Instant now = clock.instant();
    return issue(
        farmer, deviceId, UUID.randomUUID(), now.plus(props.farmerSession().refreshIdleTtl()), now);
  }

  /** Sessão nova do coordenador no painel (refresh vai no cookie). */
  @Transactional
  public Session startWebSession(AppUser coordinator) {
    Instant now = clock.instant();
    return issue(coordinator, null, UUID.randomUUID(), webExpiry(now, now), now);
  }

  /**
   * Troca um refresh token por um novo (rotação). Reutilizar um token já trocado revoga a família
   * inteira, por suspeita de roubo (RN04).
   *
   * @param deviceId aparelho que está renovando; {@code null} no web
   * @throws ApiException 401 REFRESH_INVALID ou DEVICE_REVOKED
   */
  @Transactional(noRollbackFor = ApiException.class)
  public Session refresh(String rawToken, UUID deviceId) {
    Instant now = clock.instant();
    var token =
        refreshTokens
            .findByTokenHash(SecureTokens.sha256Hex(rawToken))
            .filter(t -> Objects.equals(t.getDeviceId(), deviceId))
            .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_INVALID));

    if (deviceId != null && devices.findById(deviceId).map(d -> d.isRevoked()).orElse(true)) {
      throw new ApiException(ErrorCode.DEVICE_REVOKED);
    }
    if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) {
      throw new ApiException(ErrorCode.REFRESH_INVALID);
    }
    if (token.getUsedAt() != null) {
      if (Duration.between(token.getUsedAt(), now).compareTo(REUSE_GRACE) > 0) {
        log.warn("Refresh token reutilizado; família {} revogada", token.getFamilyId());
        refreshTokens.revokeFamily(token.getFamilyId(), now);
        throw new ApiException(ErrorCode.REFRESH_INVALID);
      }
      // Dentro da tolerância: o sucessor nunca chegou ao cliente; ele é descartado.
      refreshTokens.findByFamilyId(token.getFamilyId()).stream()
          .filter(t -> t.getCreatedAt().isAfter(token.getCreatedAt()) && t.getRevokedAt() == null)
          .forEach(t -> t.revoke(now));
    }

    var user =
        users
            .findById(token.getUserId())
            .filter(u -> u.isActive() && !u.isDeleted())
            .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_INVALID));
    token.markUsed(now);
    Instant expiresAt =
        deviceId != null
            ? now.plus(props.farmerSession().refreshIdleTtl())
            : webExpiry(refreshTokens.familyStartedAt(token.getFamilyId()), now);
    if (!expiresAt.isAfter(now)) {
      throw new ApiException(ErrorCode.REFRESH_INVALID); // sessão web passou dos 7 dias
    }
    return issue(user, deviceId, token.getFamilyId(), expiresAt, now);
  }

  /** Revoga a família do token informado (logout do web). Token desconhecido é ignorado. */
  @Transactional
  public void revokeByToken(String rawToken) {
    refreshTokens
        .findByTokenHash(SecureTokens.sha256Hex(rawToken))
        .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), clock.instant()));
  }

  /** Derruba todas as sessões do usuário (troca de senha, desativação). */
  @Transactional(propagation = Propagation.MANDATORY)
  public void revokeAllForUser(UUID userId) {
    refreshTokens.revokeAllForUser(userId, clock.instant());
  }

  /** Derruba as sessões de um aparelho (revogação ou logout do app). */
  @Transactional(propagation = Propagation.MANDATORY)
  public void revokeAllForDevice(UUID deviceId) {
    refreshTokens.revokeAllForDevice(deviceId, clock.instant());
  }

  /** Validade do token de acesso, em segundos. */
  public long accessTokenTtlSeconds() {
    return props.jwt().accessTtl().toSeconds();
  }

  /** Sessão web: 12 h sem uso, no máximo 7 dias desde o login (RN05). */
  private Instant webExpiry(Instant familyStartedAt, Instant now) {
    Instant idle = now.plus(props.coordinatorSession().refreshIdleTtl());
    Instant max = familyStartedAt.plus(props.coordinatorSession().refreshMaxTtl());
    return idle.isBefore(max) ? idle : max;
  }

  private Session issue(
      AppUser user, UUID deviceId, UUID familyId, Instant expiresAt, Instant now) {
    String raw = SecureTokens.newToken();
    refreshTokens.save(
        new RefreshToken(
            user.getId(), deviceId, familyId, SecureTokens.sha256Hex(raw), expiresAt, now));
    return new Session(
        user, accessToken(user, deviceId, now), accessTokenTtlSeconds(), raw, expiresAt);
  }

  /** JWT HS256 com {@code sub}, {@code role}, {@code assoc} e, no app, {@code dev}. */
  private String accessToken(AppUser user, UUID deviceId, Instant now) {
    var claims =
        JwtClaimsSet.builder()
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plus(props.jwt().accessTtl()))
            .claim(CurrentUserProvider.CLAIM_ROLE, user.getRole().name())
            .claim(CurrentUserProvider.CLAIM_ASSOCIATION, user.getAssociationId().toString());
    if (deviceId != null) {
      claims.claim(CurrentUserProvider.CLAIM_DEVICE, deviceId.toString());
    }
    var header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
  }
}
