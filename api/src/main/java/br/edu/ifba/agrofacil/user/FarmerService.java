// Cadastro e gestão de agricultores pelo coordenador, incluindo códigos de acesso (RN01–RN03,
// RN25).
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.association.Association;
import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.catalog.MunicipalityNames;
import br.edu.ifba.agrofacil.common.domain.AddressValue;
import br.edu.ifba.agrofacil.common.domain.Cpf;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.security.Role;
import br.edu.ifba.agrofacil.common.security.SecureTokens;
import br.edu.ifba.agrofacil.common.web.IdName;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import br.edu.ifba.agrofacil.common.web.VersionCheck;
import br.edu.ifba.agrofacil.group.GroupRepository;
import br.edu.ifba.agrofacil.group.SpgGroup;
import br.edu.ifba.agrofacil.property.PropertyService;
import br.edu.ifba.agrofacil.property.PropertySummary;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class FarmerService {

  private static final Logger log = LoggerFactory.getLogger(FarmerService.class);

  /** Ordem do pior para o melhor status, para a coluna "situação" da lista. */
  private static final List<String> STATUS_SEVERITY =
      List.of("EXPIRED", "SUSPENDED", "CANCELLED", "NONE", "EXPIRING", "VALID");

  private final UserRepository users;
  private final FarmerProfileRepository profiles;
  private final GroupRepository groups;
  private final DeviceRepository devices;
  private final DeviceService deviceService;
  private final AccessCodeRepository accessCodes;
  private final PropertyService properties;
  private final AssociationService associations;
  private final MunicipalityNames municipalities;
  private final FarmerStats stats;
  private final PasswordEncoder passwordEncoder;
  private final JsonPatch jsonPatch;
  private final Clock clock;

  public FarmerService(
      UserRepository users,
      FarmerProfileRepository profiles,
      GroupRepository groups,
      DeviceRepository devices,
      DeviceService deviceService,
      AccessCodeRepository accessCodes,
      PropertyService properties,
      AssociationService associations,
      MunicipalityNames municipalities,
      FarmerStats stats,
      PasswordEncoder passwordEncoder,
      JsonPatch jsonPatch,
      Clock clock) {
    this.users = users;
    this.profiles = profiles;
    this.groups = groups;
    this.devices = devices;
    this.deviceService = deviceService;
    this.accessCodes = accessCodes;
    this.properties = properties;
    this.associations = associations;
    this.municipalities = municipalities;
    this.stats = stats;
    this.passwordEncoder = passwordEncoder;
    this.jsonPatch = jsonPatch;
    this.clock = clock;
  }

  /** Lista paginada com os indicadores de cada agricultor. */
  @Transactional(readOnly = true)
  public PageResponse<FarmerListItem> list(CurrentUser user, FarmerFilter filter, Pageable page) {
    var result = users.findAll(specFor(user.associationId(), filter), page);
    return PageResponse.of(result, listItems(result.getContent(), user.associationId()));
  }

  /** Itens da lista para os agricultores informados (também usado no detalhe do grupo). */
  @Transactional(readOnly = true)
  public List<FarmerListItem> listItems(List<AppUser> farmers, UUID associationId) {
    if (farmers.isEmpty()) {
      return List.of();
    }
    var ids = farmers.stream().map(AppUser::getId).toList();
    var association = associations.get(associationId);
    Map<UUID, FarmerProfile> profileById =
        profiles.findByUserIdIn(ids).stream()
            .collect(Collectors.toMap(FarmerProfile::getUserId, Function.identity()));
    Map<UUID, SpgGroup> groupById =
        groups
            .findByIdIn(
                profileById.values().stream()
                    .map(FarmerProfile::getGroupId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList())
            .stream()
            .filter(g -> !g.isDeleted())
            .collect(Collectors.toMap(SpgGroup::getId, Function.identity()));
    Map<UUID, Instant> lastSeen =
        devices.lastSeenByUser(ids).stream()
            .filter(row -> row[1] != null)
            .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Instant) row[1]));
    var recordStats = stats.records(ids);
    var summaries = properties.summariesByFarmer(ids, association);

    return farmers.stream()
        .map(f -> {
          var profile = profileById.get(f.getId());
          var group = profile == null ? null : groupById.get(profile.getGroupId());
          var municipalityId =
              profile == null || profile.getAddress() == null
                  ? null
                  : profile.getAddress().getMunicipalityId();
          var props = summaries.getOrDefault(f.getId(), List.of());
          var rs = recordStats.get(f.getId());
          return new FarmerListItem(
              f.getId(),
              f.getName(),
              Cpf.mask(f.getCpf()),
              f.getPhone(),
              f.getStatus(),
              group == null ? null : new IdName(group.getId(), group.getName()),
              municipalityId == null ? null : municipalities.nameOf(municipalityId),
              props.size(),
              lastSeen.get(f.getId()),
              rs == null ? null : rs.lastRecordOn(),
              rs == null ? 0 : rs.incompleteRecords(),
              worstStatus(props));
        })
        .toList();
  }

  @Transactional(readOnly = true)
  public FarmerView get(CurrentUser user, UUID id) {
    return view(findFarmer(user, id));
  }

  /**
   * Cadastra o agricultor. Ele entra pelo app com um código de acesso gerado depois.
   *
   * @throws ApiException 422 INVALID_CPF, 409 CPF_IN_USE ou EMAIL_IN_USE
   */
  @Transactional
  public FarmerView create(CurrentUser user, FarmerWrite write) {
    String cpf = validCpf(write.cpf());
    if (users.existsByCpfAndDeletedAtIsNull(cpf)) {
      throw new ApiException(ErrorCode.CPF_IN_USE);
    }
    var farmer = AppUser.newFarmer(user.associationId(), write.name().trim(), cpf);
    var profile = new FarmerProfile(farmer.getId());
    apply(user, farmer, profile, write);
    users.saveAndFlush(farmer);
    profiles.save(profile);
    return view(farmer);
  }

  /** Edição parcial. Mudar o grupo faz o app puxar tudo de novo (os comunicados mudam). */
  @Transactional
  public FarmerView patch(CurrentUser user, UUID id, JsonNode body) {
    var farmer = findFarmer(user, id);
    var current = view(farmer);
    var write = jsonPatch.merge(current.toWrite(), body, FarmerWrite.class);
    VersionCheck.ensure(body, write.version(), farmer.getVersion());
    String cpf = validCpf(write.cpf());
    if (!cpf.equals(farmer.getCpf()) && users.existsByCpfAndDeletedAtIsNull(cpf)) {
      throw new ApiException(ErrorCode.CPF_IN_USE);
    }
    var profile = profiles.findById(id).orElseGet(() -> new FarmerProfile(id));
    if (!Objects.equals(profile.getGroupId(), write.groupId())) {
      farmer.requestSyncReset(clock.instant());
    }
    farmer.setName(write.name().trim());
    farmer.setCpf(cpf);
    apply(user, farmer, profile, write);
    profiles.save(profile);
    // A ficha também muda o app_user, para a versão e o sync_seq andarem juntos.
    farmer.touch();
    return view(users.saveAndFlush(farmer));
  }

  /** Desativa: os dados ficam, os aparelhos e códigos pendentes são revogados (RN25). */
  @Transactional
  public void deactivate(CurrentUser user, UUID id) {
    var farmer = findFarmer(user, id);
    farmer.setStatus(UserStatus.INACTIVE);
    deviceService.revokeAll(id, user.id());
    accessCodes
        .findByFarmerIdAndUsedAtIsNullAndRevokedAtIsNull(id)
        .forEach(c -> c.revoke(clock.instant()));
    log.info("Agricultor {} desativado", id);
  }

  @Transactional
  public void reactivate(CurrentUser user, UUID id) {
    findFarmer(user, id).setStatus(UserStatus.ACTIVE);
  }

  /**
   * Gera um código de acesso novo e revoga os anteriores não usados (RN03). O código em texto só
   * aparece nesta resposta.
   */
  @Transactional
  public AccessCodeResponse newAccessCode(CurrentUser user, UUID id) {
    var farmer = findFarmer(user, id);
    if (!farmer.isActive()) {
      throw new ApiException(ErrorCode.USER_INACTIVE);
    }
    Instant now = clock.instant();
    accessCodes.findByFarmerIdAndUsedAtIsNullAndRevokedAtIsNull(id).forEach(c -> c.revoke(now));
    Association association = associations.get(user.associationId());
    Instant expiresAt = now.plus(Duration.ofHours(association.getAccessCodeTtlHours()));
    String code = SecureTokens.newSixDigitCode();
    accessCodes.save(new AccessCode(id, passwordEncoder.encode(code), expiresAt, user.id(), now));
    log.info("Código de acesso gerado para o agricultor {}", id);
    return new AccessCodeResponse(code, expiresAt);
  }

  /** Agricultor da associação do coordenador; 404 se for de outra. */
  @Transactional(readOnly = true)
  public AppUser findFarmer(CurrentUser user, UUID id) {
    return users
        .findByIdAndAssociationIdAndRoleAndDeletedAtIsNull(id, user.associationId(), Role.FARMER)
        .orElseThrow(ApiException::notFound);
  }

  private void apply(CurrentUser user, AppUser farmer, FarmerProfile profile, FarmerWrite write) {
    if (write.email() != null && users.existsOtherWithEmail(write.email(), farmer.getId())) {
      throw new ApiException(ErrorCode.EMAIL_IN_USE);
    }
    if (write.groupId() != null
        && groups
            .findByIdAndAssociationIdAndDeletedAtIsNull(write.groupId(), user.associationId())
            .isEmpty()) {
      throw ApiException.validation("groupId", "NOT_FOUND", "Grupo não encontrado.");
    }
    var municipalityId = write.address() == null ? null : write.address().municipalityId();
    if (municipalityId != null && !municipalities.exists(municipalityId)) {
      throw ApiException.validation(
          "address.municipalityId", "NOT_FOUND", "Município não encontrado.");
    }
    farmer.setEmail(write.email());
    farmer.setPhone(write.phone());
    profile.setGroupId(write.groupId());
    profile.setGroupJoinedYear(write.groupJoinedYear());
    profile.setCafNumber(write.cafNumber());
    profile.setAddress(AddressValue.from(write.address()));
    profile.setNotes(write.notes());
  }

  private FarmerView view(AppUser farmer) {
    var profile =
        profiles.findById(farmer.getId()).orElseGet(() -> new FarmerProfile(farmer.getId()));
    var association = associations.get(farmer.getAssociationId());
    List<PropertySummary> props =
        properties
            .summariesByFarmer(List.of(farmer.getId()), association)
            .getOrDefault(farmer.getId(), List.of());
    boolean pendingCode =
        accessCodes
            .findFirstByFarmerIdAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                farmer.getId())
            .filter(c -> c.isPending(clock.instant()))
            .isPresent();
    return new FarmerView(
        farmer.getId(),
        farmer.getName(),
        farmer.getCpf(),
        farmer.getEmail(),
        farmer.getPhone(),
        farmer.getStatus(),
        profile.getGroupId(),
        profile.getGroupJoinedYear(),
        profile.getCafNumber(),
        AddressValue.toDto(profile.getAddress(), municipalities::nameOf),
        profile.getNotes(),
        props,
        devices.countActive(farmer.getId()),
        pendingCode,
        farmer.getCreatedAt(),
        farmer.getVersion());
  }

  private static String validCpf(String raw) {
    if (!Cpf.isValid(raw)) {
      throw new ApiException(ErrorCode.INVALID_CPF);
    }
    return Cpf.normalize(raw);
  }

  /** Pior status entre as propriedades; {@code NONE} conta a propriedade sem certificação. */
  private static String worstStatus(Collection<PropertySummary> props) {
    return props.stream()
        .map(p ->
            p.currentCertification() == null ? "NONE" : p.currentCertification().status().name())
        .min((a, b) -> Integer.compare(STATUS_SEVERITY.indexOf(a), STATUS_SEVERITY.indexOf(b)))
        .orElse(null);
  }

  private Specification<AppUser> specFor(UUID associationId, FarmerFilter f) {
    return (root, query, cb) -> {
      var predicates = new ArrayList<Predicate>();
      predicates.add(cb.equal(root.get("associationId"), associationId));
      predicates.add(cb.equal(root.get("role"), Role.FARMER));
      predicates.add(cb.isNull(root.get("deletedAt")));
      if (f.status() != null) {
        predicates.add(cb.equal(root.get("status"), f.status()));
      }
      if (f.groupId() != null || f.municipalityId() != null) {
        var sub = query.subquery(UUID.class);
        var profile = sub.from(FarmerProfile.class);
        var conditions = new ArrayList<Predicate>();
        if (f.groupId() != null) {
          conditions.add(cb.equal(profile.get("groupId"), f.groupId()));
        }
        if (f.municipalityId() != null) {
          conditions.add(
              cb.equal(profile.get("address").get("municipalityId"), f.municipalityId()));
        }
        sub.select(profile.get("userId")).where(conditions.toArray(Predicate[]::new));
        predicates.add(root.get("id").in(sub));
      }
      if (f.search() != null && !f.search().isBlank()) {
        String text = "%" + f.search().trim().toLowerCase() + "%";
        var byName =
            cb.like(
                cb.function("f_unaccent", String.class, cb.lower(root.get("name"))),
                cb.function("f_unaccent", String.class, cb.literal(text)));
        String digits = Cpf.normalize(f.search());
        predicates.add(
            digits.isEmpty()
                ? byName
                : cb.or(byName, cb.like(root.get("cpf"), "%" + digits + "%")));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
