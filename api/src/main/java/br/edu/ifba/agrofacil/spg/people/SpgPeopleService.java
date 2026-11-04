// Cadastro das pessoas do SPG no painel: colaboradores, Comissão de Avaliação e impedimentos.
package br.edu.ifba.agrofacil.spg.people;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.common.web.VersionCheck;
import br.edu.ifba.agrofacil.config.AppProperties;
import br.edu.ifba.agrofacil.group.GroupRepository;
import br.edu.ifba.agrofacil.spg.people.SpgPeopleDtos.CommitteeView;
import br.edu.ifba.agrofacil.spg.people.SpgPeopleDtos.CommitteeWrite;
import br.edu.ifba.agrofacil.spg.people.SpgPeopleDtos.ConflictView;
import br.edu.ifba.agrofacil.spg.people.SpgPeopleDtos.ConflictWrite;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Só o coordenador usa estas rotas (doc 1 §3.17); os impedimentos nunca vão para o app (RN40). */
@Service
public class SpgPeopleService {

  private final CollaboratorRepository collaborators;
  private final CommitteeMemberRepository committee;
  private final MemberConflictRepository conflicts;
  private final GroupRepository groups;
  private final PersonDirectory people;
  private final JsonPatch jsonPatch;
  private final Clock clock;
  private final AppProperties props;

  public SpgPeopleService(
      CollaboratorRepository collaborators,
      CommitteeMemberRepository committee,
      MemberConflictRepository conflicts,
      GroupRepository groups,
      PersonDirectory people,
      JsonPatch jsonPatch,
      Clock clock,
      AppProperties props) {
    this.collaborators = collaborators;
    this.committee = committee;
    this.conflicts = conflicts;
    this.groups = groups;
    this.people = people;
    this.jsonPatch = jsonPatch;
    this.clock = clock;
    this.props = props;
  }

  // Colaboradores

  @Transactional(readOnly = true)
  public List<CollaboratorDtos.View> collaborators(CurrentUser user) {
    return collaborators.findByAssociationIdOrderByName(user.associationId()).stream()
        .map(CollaboratorDtos.View::of)
        .toList();
  }

  @Transactional
  public CollaboratorDtos.View createCollaborator(CurrentUser user, CollaboratorDtos.Write write) {
    ensureGroup(user, write.groupId(), "groupId");
    var c = new Collaborator(user.associationId());
    c.update(write);
    return CollaboratorDtos.View.of(collaborators.saveAndFlush(c));
  }

  @Transactional
  public CollaboratorDtos.View patchCollaborator(CurrentUser user, UUID id, JsonNode body) {
    var c =
        collaborators
            .findByIdAndAssociationId(id, user.associationId())
            .orElseThrow(ApiException::notFound);
    var write =
        jsonPatch.merge(CollaboratorDtos.View.of(c).toWrite(), body, CollaboratorDtos.Write.class);
    VersionCheck.ensure(body, write.version(), c.getVersion());
    ensureGroup(user, write.groupId(), "groupId");
    c.update(write);
    return CollaboratorDtos.View.of(collaborators.saveAndFlush(c));
  }

  // Comissão de Avaliação

  @Transactional(readOnly = true)
  public List<CommitteeView> committee(CurrentUser user) {
    var list = committee.findByAssociationIdOrderByStartedOnDesc(user.associationId());
    var names =
        people.names(
            list.stream().map(CommitteeMember::getFarmerId).filter(Objects::nonNull).toList(),
            list.stream()
                .map(CommitteeMember::getCollaboratorId)
                .filter(Objects::nonNull)
                .toList());
    return list.stream().map(m -> view(m, names.get(personId(m)))).toList();
  }

  @Transactional
  public CommitteeView createCommitteeMember(CurrentUser user, CommitteeWrite write) {
    var person = people.resolve(user.associationId(), write.person(), "person");
    var m =
        new CommitteeMember(
            user.associationId(),
            person.isFarmer() ? person.id() : null,
            person.isFarmer() ? null : person.id());
    applyCommittee(user, m, write);
    return view(committee.saveAndFlush(m), person.name());
  }

  /** Muda grupo, papel ou datas; encerrar a participação é preencher {@code endedOn}. */
  @Transactional
  public CommitteeView patchCommitteeMember(CurrentUser user, UUID id, JsonNode body) {
    var m = findMember(user, id);
    var current =
        view(
            m,
            people.names(listOf(m.getFarmerId()), listOf(m.getCollaboratorId())).get(personId(m)));
    var write = jsonPatch.merge(current.toWrite(), body, CommitteeWrite.class);
    VersionCheck.ensure(body, write.version(), m.getVersion());
    if (write.person().id() == null || !write.person().id().equals(personId(m))) {
      throw ApiException.validation(
          "person", "NOT_EDITABLE", "Para trocar a pessoa, encerre e cadastre de novo.");
    }
    applyCommittee(user, m, write);
    return view(committee.saveAndFlush(m), current.person().name());
  }

  /** Remove um lançamento errado (para encerrar a participação, use {@code endedOn}). */
  @Transactional
  public void deleteCommitteeMember(CurrentUser user, UUID id) {
    committee.delete(findMember(user, id));
  }

  // Impedimentos

  @Transactional(readOnly = true)
  public List<ConflictView> conflicts(CurrentUser user) {
    var list = conflicts.findByAssociationIdOrderByCreatedAtDesc(user.associationId());
    var names =
        people.names(
            list.stream()
                .flatMap(c -> Stream.of(c.getFarmerAId(), c.getFarmerBId()))
                .distinct()
                .toList(),
            List.of());
    return list.stream()
        .map(c -> view(c, names.get(c.getFarmerAId()), names.get(c.getFarmerBId())))
        .toList();
  }

  /**
   * @throws ApiException 409 MEMBER_CONFLICT_EXISTS se o par já tiver impedimento
   */
  @Transactional
  public ConflictView createConflict(CurrentUser user, ConflictWrite write) {
    if (write.farmerAId().equals(write.farmerBId())) {
      throw ApiException.validation("farmerBId", "SAME_PERSON", "Escolha duas pessoas diferentes.");
    }
    var a =
        people.resolve(
            user.associationId(), PersonRef.farmer(write.farmerAId(), null), "farmerAId");
    var b =
        people.resolve(
            user.associationId(), PersonRef.farmer(write.farmerBId(), null), "farmerBId");
    var conflict =
        new MemberConflict(
            user.associationId(), a.id(), b.id(), write.reason(), blankToNull(write.notes()));
    if (conflicts.existsByFarmerAIdAndFarmerBId(conflict.getFarmerAId(), conflict.getFarmerBId())) {
      throw new ApiException(ErrorCode.MEMBER_CONFLICT_EXISTS);
    }
    conflicts.saveAndFlush(conflict);
    var names = Map.of(a.id(), a.name(), b.id(), b.name());
    return view(conflict, names.get(conflict.getFarmerAId()), names.get(conflict.getFarmerBId()));
  }

  @Transactional
  public void deleteConflict(CurrentUser user, UUID id) {
    conflicts.delete(
        conflicts
            .findByIdAndAssociationId(id, user.associationId())
            .orElseThrow(ApiException::notFound));
  }

  private void applyCommittee(CurrentUser user, CommitteeMember m, CommitteeWrite write) {
    ensureGroup(user, write.representedGroupId(), "representedGroupId");
    if (write.endedOn() != null && !write.endedOn().isAfter(write.startedOn())) {
      throw ApiException.validation(
          "endedOn", "BEFORE_START", "O fim precisa ser depois do início.");
    }
    m.update(write.representedGroupId(), write.role(), write.startedOn(), write.endedOn());
  }

  private CommitteeMember findMember(CurrentUser user, UUID id) {
    return committee
        .findByIdAndAssociationId(id, user.associationId())
        .orElseThrow(ApiException::notFound);
  }

  private void ensureGroup(CurrentUser user, UUID groupId, String field) {
    if (groupId != null
        && groups
            .findByIdAndAssociationIdAndDeletedAtIsNull(groupId, user.associationId())
            .isEmpty()) {
      throw ApiException.validation(field, "NOT_FOUND", "Grupo não encontrado.");
    }
  }

  private CommitteeView view(CommitteeMember m, String name) {
    var person =
        m.getFarmerId() != null
            ? PersonRef.farmer(m.getFarmerId(), name)
            : PersonRef.collaborator(m.getCollaboratorId(), name);
    return new CommitteeView(
        m.getId(),
        person,
        m.getRepresentedGroupId(),
        m.getRole(),
        m.getStartedOn(),
        m.getEndedOn(),
        m.isActiveOn(LocalDate.now(clock.withZone(props.zone()))),
        m.getVersion());
  }

  private static ConflictView view(MemberConflict c, String nameA, String nameB) {
    return new ConflictView(
        c.getId(), c.getFarmerAId(), nameA, c.getFarmerBId(), nameB, c.getReason(), c.getNotes());
  }

  private static UUID personId(CommitteeMember m) {
    return m.getFarmerId() != null ? m.getFarmerId() : m.getCollaboratorId();
  }

  private static List<UUID> listOf(UUID id) {
    return id == null ? List.of() : List.of(id);
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
