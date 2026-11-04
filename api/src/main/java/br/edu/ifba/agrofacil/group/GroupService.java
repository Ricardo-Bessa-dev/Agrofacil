// Grupos do SPG: cadastro pelo coordenador e contagem de agricultores (RN01).
package br.edu.ifba.agrofacil.group;

import br.edu.ifba.agrofacil.catalog.MunicipalityNames;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.common.web.VersionCheck;
import br.edu.ifba.agrofacil.user.FarmerProfileRepository;
import br.edu.ifba.agrofacil.user.FarmerService;
import br.edu.ifba.agrofacil.user.UserStatus;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class GroupService {

  private final GroupRepository groups;
  private final FarmerProfileRepository profiles;
  private final FarmerService farmers;
  private final MunicipalityNames municipalities;
  private final JsonPatch jsonPatch;
  private final Clock clock;

  public GroupService(
      GroupRepository groups,
      FarmerProfileRepository profiles,
      FarmerService farmers,
      MunicipalityNames municipalities,
      JsonPatch jsonPatch,
      Clock clock) {
    this.groups = groups;
    this.profiles = profiles;
    this.farmers = farmers;
    this.municipalities = municipalities;
    this.jsonPatch = jsonPatch;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<GroupView> list(CurrentUser user) {
    return views(groups.findByAssociationIdAndDeletedAtIsNullOrderByName(user.associationId()));
  }

  @Transactional(readOnly = true)
  public GroupDetail get(CurrentUser user, UUID id) {
    var group = find(user, id);
    return new GroupDetail(
        views(List.of(group)).getFirst(),
        farmers.listItems(profiles.findFarmersOfGroup(id), user.associationId()));
  }

  @Transactional
  public GroupView create(CurrentUser user, GroupWrite write) {
    var group = new SpgGroup(user.associationId());
    apply(group, write);
    return views(List.of(groups.saveAndFlush(group))).getFirst();
  }

  @Transactional
  public GroupView patch(CurrentUser user, UUID id, JsonNode body) {
    var group = find(user, id);
    var write = jsonPatch.merge(views(List.of(group)).getFirst().toWrite(), body, GroupWrite.class);
    VersionCheck.ensure(body, write.version(), group.getVersion());
    apply(group, write);
    return views(List.of(groups.saveAndFlush(group))).getFirst();
  }

  /**
   * Exclusão lógica.
   *
   * @throws ApiException 409 GROUP_NOT_EMPTY se ainda houver agricultores
   */
  @Transactional
  public void delete(CurrentUser user, UUID id) {
    var group = find(user, id);
    if (!profiles.findFarmersOfGroup(id).isEmpty()) {
      throw new ApiException(ErrorCode.GROUP_NOT_EMPTY);
    }
    group.markDeleted(clock.instant());
  }

  private SpgGroup find(CurrentUser user, UUID id) {
    return groups
        .findByIdAndAssociationIdAndDeletedAtIsNull(id, user.associationId())
        .orElseThrow(ApiException::notFound);
  }

  private void apply(SpgGroup group, GroupWrite write) {
    if (write.municipalityId() != null && !municipalities.exists(write.municipalityId())) {
      throw ApiException.validation("municipalityId", "NOT_FOUND", "Município não encontrado.");
    }
    group.update(write);
  }

  private List<GroupView> views(List<SpgGroup> list) {
    Map<UUID, Long> counts =
        list.isEmpty()
            ? Map.of()
            : profiles
                .countByGroup(list.stream().map(SpgGroup::getId).toList(), UserStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    return list.stream()
        .map(g -> {
          long count = counts.getOrDefault(g.getId(), 0L);
          return new GroupView(
              g.getId(),
              g.getName(),
              g.getMunicipalityId(),
              g.getMunicipalityId() == null ? null : municipalities.nameOf(g.getMunicipalityId()),
              g.getNotes(),
              count,
              count < SpgGroup.MIN_FARMERS,
              g.getVersion());
        })
        .toList();
  }
}
