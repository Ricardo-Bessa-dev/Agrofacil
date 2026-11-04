// Atalhos para montar o cenário dos testes direto pelos repositórios e serviços.
package br.edu.ifba.agrofacil.support;

import br.edu.ifba.agrofacil.auth.TokenService;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.security.Role;
import br.edu.ifba.agrofacil.property.PropertyService;
import br.edu.ifba.agrofacil.property.PropertyView;
import br.edu.ifba.agrofacil.property.PropertyWrite;
import br.edu.ifba.agrofacil.user.AppUser;
import br.edu.ifba.agrofacil.user.Device;
import br.edu.ifba.agrofacil.user.DeviceRepository;
import br.edu.ifba.agrofacil.user.FarmerProfile;
import br.edu.ifba.agrofacil.user.FarmerProfileRepository;
import br.edu.ifba.agrofacil.user.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Fixtures {

  /** Associação criada pela migração V5. */
  public static final UUID ASSOCIATION = UUID.fromString("00000000-0000-0000-0000-000000000001");

  public static final String PASSWORD = "uma-senha-bem-longa-42";

  private final UserRepository users;
  private final FarmerProfileRepository profiles;
  private final DeviceRepository devices;
  private final PropertyService properties;
  private final TokenService tokens;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public Fixtures(
      UserRepository users,
      FarmerProfileRepository profiles,
      DeviceRepository devices,
      PropertyService properties,
      TokenService tokens,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.users = users;
    this.profiles = profiles;
    this.devices = devices;
    this.properties = properties;
    this.tokens = tokens;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  /** Agricultor com aparelho ativo, pronto para usar o app. */
  public record Farmer(AppUser user, UUID deviceId, String accessToken) {
    public CurrentUser current() {
      return new CurrentUser(user.getId(), Role.FARMER, ASSOCIATION, deviceId);
    }
  }

  public record Coordinator(AppUser user, String accessToken) {
    public CurrentUser current() {
      return new CurrentUser(user.getId(), Role.COORDINATOR, ASSOCIATION, null);
    }
  }

  /** Coordenador ativo, com senha {@link #PASSWORD}. */
  @Transactional
  public Coordinator coordinator() {
    var user = AppUser.newInvitedCoordinator(ASSOCIATION, "Coordenadora Teste", TestData.email());
    user.acceptInvite("Coordenadora Teste", passwordEncoder.encode(PASSWORD));
    users.saveAndFlush(user);
    return new Coordinator(user, tokens.startWebSession(user).accessToken());
  }

  /** Agricultor ativo com um aparelho já ativado. */
  @Transactional
  public Farmer farmer(String name) {
    var user = users.saveAndFlush(AppUser.newFarmer(ASSOCIATION, name, TestData.cpf()));
    profiles.save(new FarmerProfile(user.getId()));
    var device = devices.save(new Device(UUID.randomUUID(), user.getId(), clock.instant()));
    device.activateFor(user.getId(), "Celular de teste", "ANDROID", "1.0.0", clock.instant());
    return new Farmer(
        user, device.getId(), tokens.startFarmerSession(user, device.getId()).accessToken());
  }

  /** Propriedade do agricultor, criada pelo coordenador (já com a "Área principal"). */
  public PropertyView property(Coordinator coordinator, Farmer farmer, String name) {
    return properties.create(
        coordinator.current(),
        new PropertyWrite(null, farmer.user().getId(), name, null, null, null, null, null, null));
  }
}
