// Troca CPF + código de acesso por uma sessão vinculada ao aparelho (ADR-03, RN03, RN25).
package br.edu.ifba.agrofacil.auth;

import br.edu.ifba.agrofacil.common.domain.Cpf;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.RateLimiter;
import br.edu.ifba.agrofacil.common.security.Role;
import br.edu.ifba.agrofacil.notification.NotificationService;
import br.edu.ifba.agrofacil.notification.NotificationType;
import br.edu.ifba.agrofacil.user.AccessCodeRepository;
import br.edu.ifba.agrofacil.user.DeviceRepository;
import br.edu.ifba.agrofacil.user.DeviceService;
import br.edu.ifba.agrofacil.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmerActivationService {

  private static final Logger log = LoggerFactory.getLogger(FarmerActivationService.class);
  private static final int MAX_PER_CPF_PER_HOUR = 5;
  private static final int MAX_PER_IP_PER_HOUR = 20;

  private final UserRepository users;
  private final AccessCodeRepository accessCodes;
  private final DeviceRepository devices;
  private final DeviceService deviceService;
  private final TokenService tokens;
  private final NotificationService notifications;
  private final PasswordEncoder passwordEncoder;
  private final RateLimiter rateLimiter;
  private final Clock clock;

  public FarmerActivationService(
      UserRepository users,
      AccessCodeRepository accessCodes,
      DeviceRepository devices,
      DeviceService deviceService,
      TokenService tokens,
      NotificationService notifications,
      PasswordEncoder passwordEncoder,
      RateLimiter rateLimiter,
      Clock clock) {
    this.users = users;
    this.accessCodes = accessCodes;
    this.devices = devices;
    this.deviceService = deviceService;
    this.tokens = tokens;
    this.notifications = notifications;
    this.passwordEncoder = passwordEncoder;
    this.rateLimiter = rateLimiter;
    this.clock = clock;
  }

  /**
   * Ativa o aparelho. CPF desconhecido e código errado dão a mesma resposta, para não revelar quem
   * está cadastrado.
   *
   * @throws ApiException INVALID_ACCESS_CODE, ACCESS_CODE_LOCKED, USER_INACTIVE ou RATE_LIMITED
   */
  // O contador de tentativas precisa ser gravado mesmo quando a resposta é erro.
  @Transactional(noRollbackFor = ApiException.class)
  public TokenService.Session activate(AuthDtos.ActivateRequest req, String clientIp) {
    String cpf = Cpf.normalize(req.cpf());
    rateLimiter.check("activate:cpf:" + cpf, MAX_PER_CPF_PER_HOUR, Duration.ofHours(1));
    rateLimiter.check("activate:ip:" + clientIp, MAX_PER_IP_PER_HOUR, Duration.ofHours(1));
    Instant now = clock.instant();

    var farmer =
        users
            .findByCpfAndRoleAndDeletedAtIsNull(cpf, Role.FARMER)
            .orElseThrow(() -> new ApiException(ErrorCode.INVALID_ACCESS_CODE));
    if (!farmer.isActive()) {
      throw new ApiException(ErrorCode.USER_INACTIVE); // RN25
    }
    var code =
        accessCodes
            .findFirstByFarmerIdAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                farmer.getId())
            .filter(c -> c.getExpiresAt().isAfter(now))
            .orElseThrow(() -> new ApiException(ErrorCode.INVALID_ACCESS_CODE));
    if (code.isLocked()) {
      throw new ApiException(ErrorCode.ACCESS_CODE_LOCKED);
    }
    if (!passwordEncoder.matches(req.code(), code.getCodeHash())) {
      code.registerFailedAttempt();
      log.info("Código de acesso errado para o CPF {}", Cpf.mask(cpf));
      throw new ApiException(
          code.isLocked() ? ErrorCode.ACCESS_CODE_LOCKED : ErrorCode.INVALID_ACCESS_CODE);
    }

    code.markUsed(now);
    boolean hadOtherDevices =
        devices.findByUserIdAndRevokedAtIsNull(farmer.getId()).stream()
            .anyMatch(d -> !d.getId().equals(req.device().id()));
    var device = deviceService.registerOrReactivate(farmer, req.device());
    farmer.setLastLoginAt(now);
    if (hadOtherDevices) {
      // Alerta contra quem pediu um código se passando pelo agricultor (doc 1 §5.2).
      notifications.notify(
          List.of(farmer.getId()),
          NotificationType.DEVICE_ACTIVATED,
          "Seu acesso foi ativado em outro celular",
          "Se não foi você, fale com a associação.",
          Map.of("deviceId", device.getId().toString()));
    }
    log.info("Aparelho ativado para o CPF {}", Cpf.mask(cpf));
    return tokens.startFarmerSession(farmer, device.getId());
  }
}
