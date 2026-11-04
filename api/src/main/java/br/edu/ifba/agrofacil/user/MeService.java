// Monta e altera o perfil do usuário logado (doc 1 §3.4).
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.catalog.MunicipalityNames;
import br.edu.ifba.agrofacil.common.domain.AddressValue;
import br.edu.ifba.agrofacil.common.domain.Decimals;
import br.edu.ifba.agrofacil.common.domain.PasswordPolicy;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.IdName;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.config.AppProperties;
import br.edu.ifba.agrofacil.group.GroupRepository;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class MeService {

  private final UserRepository users;
  private final FarmerProfileRepository profiles;
  private final GroupRepository groups;
  private final AssociationService associations;
  private final MunicipalityNames municipalities;
  private final PasswordEncoder passwordEncoder;
  private final JsonPatch jsonPatch;
  private final AppProperties props;
  private final Clock clock;

  public MeService(
      UserRepository users,
      FarmerProfileRepository profiles,
      GroupRepository groups,
      AssociationService associations,
      MunicipalityNames municipalities,
      PasswordEncoder passwordEncoder,
      JsonPatch jsonPatch,
      AppProperties props,
      Clock clock) {
    this.users = users;
    this.profiles = profiles;
    this.groups = groups;
    this.associations = associations;
    this.municipalities = municipalities;
    this.passwordEncoder = passwordEncoder;
    this.jsonPatch = jsonPatch;
    this.props = props;
    this.clock = clock;
  }

  /** Perfil do usuário logado. */
  @Transactional(readOnly = true)
  public Me get(CurrentUser current) {
    return build(load(current));
  }

  /** Perfil de um usuário já carregado (usado no login e no pull). */
  @Transactional(readOnly = true)
  public Me build(AppUser user) {
    var association = associations.get(user.getAssociationId());
    Me.MeFarmer farmer = null;
    if (user.isFarmer()) {
      var profile =
          profiles.findById(user.getId()).orElseGet(() -> new FarmerProfile(user.getId()));
      IdName group =
          profile.getGroupId() == null
              ? null
              : groups
                  .findById(profile.getGroupId())
                  .filter(g -> !g.isDeleted())
                  .map(g -> new IdName(g.getId(), g.getName()))
                  .orElse(null);
      farmer =
          new Me.MeFarmer(
              group,
              profile.getCafNumber(),
              AddressValue.toDto(profile.getAddress(), municipalities::nameOf));
    }
    return new Me(
        user.getId(),
        user.getRole(),
        user.getName(),
        user.isFarmer() ? user.getCpf() : null,
        user.getEmail(),
        user.getPhone(),
        new Me.MeAssociation(
            association.getId(),
            association.getName(),
            association.getWhatsapp(),
            Decimals.round(association.getTarefaM2())),
        farmer,
        user.getTermsVersion(),
        props.termsVersion());
  }

  /**
   * Alteração parcial do próprio perfil. Nome, CPF e grupo do agricultor só mudam pelo coordenador.
   */
  @Transactional
  public Me patch(CurrentUser current, JsonNode body) {
    var user = load(current);
    var me = build(user);
    var base =
        new MeWrite(
            me.name(),
            me.email(),
            me.phone(),
            me.farmer() == null ? null : new MeWrite.FarmerPart(me.farmer().address()));
    var write = jsonPatch.merge(base, body, MeWrite.class);
    user.setPhone(write.phone());
    if (user.isFarmer()) {
      if (write.email() != null && users.existsOtherWithEmail(write.email(), user.getId())) {
        throw new ApiException(ErrorCode.EMAIL_IN_USE);
      }
      user.setEmail(write.email());
      if (write.farmer() != null) {
        var profile =
            profiles.findById(user.getId()).orElseGet(() -> new FarmerProfile(user.getId()));
        profile.setAddress(AddressValue.from(write.farmer().address()));
        profiles.save(profile);
      }
    } else {
      user.setName(write.name().trim());
    }
    user.touch(); // o perfil vai no pull: a versão do usuário precisa andar
    return build(users.saveAndFlush(user));
  }

  /** Registra o aceite do termo de uso e do aviso de privacidade (RNF06). */
  @Transactional
  public void acceptTerms(CurrentUser current, String version) {
    if (!props.termsVersion().equals(version)) {
      throw ApiException.validation(
          "version", "OUTDATED_TERMS", "Esta não é a versão atual do termo.");
    }
    load(current).acceptTerms(version, clock.instant());
  }

  /** Troca de senha do coordenador logado (RN05). */
  @Transactional
  public void changePassword(CurrentUser current, String currentPassword, String newPassword) {
    var user = load(current);
    if (user.getPasswordHash() == null
        || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw ApiException.validation(
          "currentPassword", "INVALID_PASSWORD", "A senha atual não confere.");
    }
    if (!PasswordPolicy.isStrong(newPassword)) {
      throw new ApiException(ErrorCode.WEAK_PASSWORD);
    }
    user.setPasswordHash(passwordEncoder.encode(newPassword));
  }

  private AppUser load(CurrentUser current) {
    return users
        .findByIdAndAssociationIdAndDeletedAtIsNull(current.id(), current.associationId())
        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
  }
}
