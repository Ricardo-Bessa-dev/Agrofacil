// Ciclo de vida dos aparelhos: ativação, último contato, revogação (RN04, RN25).
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.auth.TokenService;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

  private final DeviceRepository devices;
  private final UserRepository users;
  private final TokenService tokens;
  private final Clock clock;

  public DeviceService(
      DeviceRepository devices, UserRepository users, TokenService tokens, Clock clock) {
    this.devices = devices;
    this.users = users;
    this.tokens = tokens;
    this.clock = clock;
  }

  /**
   * Cria ou reativa o aparelho para o agricultor. Se o celular era de outra pessoa, as sessões
   * antigas dele caem.
   */
  @Transactional
  public Device registerOrReactivate(AppUser farmer, DeviceInfo info) {
    Instant now = clock.instant();
    var device =
        devices.findById(info.id()).orElseGet(() -> new Device(info.id(), farmer.getId(), now));
    if (!device.getUserId().equals(farmer.getId())) {
      tokens.revokeAllForDevice(device.getId());
    }
    device.activateFor(farmer.getId(), info.name(), info.platform(), info.appVersion(), now);
    return devices.save(device);
  }

  /**
   * Atualiza o último contato do aparelho do agricultor (todo push e pull).
   *
   * @throws ApiException 401 DEVICE_REVOKED se o aparelho foi revogado
   */
  @Transactional
  public Device touch(CurrentUser user, UUID deviceId) {
    var device =
        devices
            .findById(deviceId)
            .filter(d -> d.getUserId().equals(user.id()) && deviceId.equals(user.deviceId()))
            .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_REVOKED));
    if (device.isRevoked()) {
      throw new ApiException(ErrorCode.DEVICE_REVOKED);
    }
    device.touch(clock.instant());
    return device;
  }

  /** Atualiza token de push, versão e nome do aparelho atual (PUT /me/device). */
  @Transactional
  public void updateCurrent(CurrentUser user, MeDeviceUpdate update) {
    var device = touch(user, user.deviceId());
    device.setPushToken(update.pushToken());
    device.setAppVersion(update.appVersion());
    if (update.name() != null) {
      device.setName(update.name());
    }
  }

  /** Aparelhos de um agricultor da associação, do mais novo para o mais antigo. */
  @Transactional(readOnly = true)
  public List<DeviceView> listForFarmer(CurrentUser coordinator, UUID farmerId) {
    findFarmer(coordinator, farmerId);
    return devices.findByUserIdOrderByCreatedAtDesc(farmerId).stream().map(DeviceView::of).toList();
  }

  /** Revogação pelo coordenador: o aparelho perde a sessão na próxima renovação. */
  @Transactional
  public void revokeByCoordinator(CurrentUser coordinator, UUID deviceId) {
    var device = devices.findById(deviceId).orElseThrow(ApiException::notFound);
    findFarmer(coordinator, device.getUserId());
    revoke(device, coordinator.id());
  }

  /** Logout do app: revoga o próprio aparelho e os tokens dele. */
  @Transactional
  public void logout(CurrentUser farmer, UUID deviceId) {
    devices
        .findById(deviceId)
        .filter(d -> d.getUserId().equals(farmer.id()))
        .ifPresent(d -> revoke(d, farmer.id()));
  }

  /** Revoga todos os aparelhos do agricultor (desativação, RN25). */
  @Transactional
  public void revokeAll(UUID farmerId, UUID by) {
    devices.findByUserIdAndRevokedAtIsNull(farmerId).forEach(d -> revoke(d, by));
  }

  private void revoke(Device device, UUID by) {
    device.revoke(by, clock.instant());
    tokens.revokeAllForDevice(device.getId());
  }

  private void findFarmer(CurrentUser coordinator, UUID farmerId) {
    users
        .findByIdAndAssociationIdAndDeletedAtIsNull(farmerId, coordinator.associationId())
        .filter(AppUser::isFarmer)
        .orElseThrow(ApiException::notFound);
  }
}
