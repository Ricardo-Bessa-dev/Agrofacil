// Carrega em lote os dados de visitado e candidatos para as regras RN32 e RN33, sem consulta por
// pessoa.
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.spg.people.CommitteeMember;
import br.edu.ifba.agrofacil.spg.people.CommitteeMemberRepository;
import br.edu.ifba.agrofacil.spg.people.MemberConflict;
import br.edu.ifba.agrofacil.spg.people.MemberConflictRepository;
import br.edu.ifba.agrofacil.spg.people.PersonDirectory;
import br.edu.ifba.agrofacil.spg.people.PersonRef;
import br.edu.ifba.agrofacil.spg.visit.VisitorModel.Candidate;
import br.edu.ifba.agrofacil.spg.visit.VisitorModel.Place;
import br.edu.ifba.agrofacil.spg.visit.VisitorModel.Target;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class VisitorDirectory {

  /** Janela do rodízio e da troca (doc 1 §6.4). */
  private static final int HISTORY_MONTHS = 12;

  /** Janela do equilíbrio: visitas já marcadas nos próximos dias. */
  private static final int AGENDA_DAYS = 30;

  private final JdbcClient jdbc;
  private final CommitteeMemberRepository committee;
  private final MemberConflictRepository conflicts;
  private final VisitParticipantRepository participants;
  private final PersonDirectory people;

  public VisitorDirectory(
      JdbcClient jdbc,
      CommitteeMemberRepository committee,
      MemberConflictRepository conflicts,
      VisitParticipantRepository participants,
      PersonDirectory people) {
    this.jdbc = jdbc;
    this.committee = committee;
    this.conflicts = conflicts;
    this.participants = participants;
    this.people = people;
  }

  /** Propriedade visitada, com o grupo atual do dono. */
  @Transactional(readOnly = true)
  public Target target(UUID propertyId) {
    return jdbc.sql(
            "select p.id, p.farmer_id, u.name, f.group_id, p.latitude, p.longitude,"
                + " p.municipality_id from property p join app_user u on u.id = p.farmer_id"
                + " left join farmer f on f.user_id = p.farmer_id where p.id = ?")
        .param(propertyId)
        .query((rs, i) ->
            new Target(
                rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class),
                rs.getString(3),
                rs.getObject(4, UUID.class),
                new Place(
                    toDouble(rs.getBigDecimal(5)),
                    toDouble(rs.getBigDecimal(6)),
                    (Integer) rs.getObject(7))))
        .single();
  }

  /** Agricultores do grupo (ativos ou não), menos o visitado: candidatos da visita de pares. */
  @Transactional(readOnly = true)
  public List<PersonRef> groupFarmers(UUID groupId, UUID exceptFarmerId) {
    if (groupId == null) {
      return List.of();
    }
    return jdbc.sql(
            "select u.id, u.name from farmer f join app_user u on u.id = f.user_id"
                + " where f.group_id = ? and u.deleted_at is null and u.id <> ? order by u.name")
        .params(groupId, exceptFarmerId)
        .query((rs, i) -> PersonRef.farmer(rs.getObject(1, UUID.class), rs.getString(2)))
        .list();
  }

  /** Membros da Comissão ativos na data: candidatos da visita de verificação. */
  @Transactional(readOnly = true)
  public List<PersonRef> committeeOn(UUID associationId, LocalDate day) {
    var active =
        committee.findByAssociationIdOrderByStartedOnDesc(associationId).stream()
            .filter(m -> m.isActiveOn(day))
            .toList();
    var names =
        people.names(
            active.stream().map(CommitteeMember::getFarmerId).filter(Objects::nonNull).toList(),
            active.stream()
                .map(CommitteeMember::getCollaboratorId)
                .filter(Objects::nonNull)
                .toList());
    var result = new LinkedHashMap<UUID, PersonRef>();
    for (var m : active) {
      var ref =
          m.getFarmerId() != null
              ? PersonRef.farmer(m.getFarmerId(), names.get(m.getFarmerId()))
              : PersonRef.collaborator(m.getCollaboratorId(), names.get(m.getCollaboratorId()));
      result.putIfAbsent(ref.id(), ref);
    }
    return List.copyOf(result.values());
  }

  /** Colaboradores ativos do grupo, para convidar como observadores. */
  @Transactional(readOnly = true)
  public List<PersonRef> groupCollaborators(UUID associationId, UUID groupId) {
    return jdbc.sql(
            "select id, name from collaborator where association_id = ? and active"
                + " and (group_id is null or group_id = ?) order by name")
        .params(associationId, groupId)
        .query((rs, i) -> PersonRef.collaborator(rs.getObject(1, UUID.class), rs.getString(2)))
        .list();
  }

  /**
   * Monta os candidatos com tudo o que as regras precisam.
   *
   * @param ignoreVisitId visita sendo editada, que não conta como "outra visita no mesmo dia"
   */
  @Transactional(readOnly = true)
  public Map<UUID, Candidate> candidates(
      UUID associationId,
      Target target,
      LocalDate plannedOn,
      LocalDate today,
      Collection<PersonRef> persons,
      UUID ignoreVisitId) {
    var farmerIds = persons.stream().filter(PersonRef::isFarmer).map(PersonRef::id).toList();
    var collaboratorIds = persons.stream().filter(p -> !p.isFarmer()).map(PersonRef::id).toList();

    Map<UUID, Boolean> active = new HashMap<>();
    Map<UUID, UUID> groupOf = new HashMap<>();
    if (!farmerIds.isEmpty()) {
      jdbc.sql(
              "select u.id, u.status = 'ACTIVE' and u.deleted_at is null, f.group_id"
                  + " from app_user u left join farmer f on f.user_id = u.id where u.id = any(?)")
          .param(farmerIds.toArray(UUID[]::new))
          .query((rs, i) -> {
            active.put(rs.getObject(1, UUID.class), rs.getBoolean(2));
            groupOf.put(rs.getObject(1, UUID.class), rs.getObject(3, UUID.class));
            return null;
          })
          .list();
    }
    if (!collaboratorIds.isEmpty()) {
      jdbc.sql("select id, active from collaborator where id = any(?)")
          .param(collaboratorIds.toArray(UUID[]::new))
          .query((rs, i) -> active.put(rs.getObject(1, UUID.class), rs.getBoolean(2)))
          .list();
    }

    Map<UUID, List<Place>> places = new HashMap<>();
    if (!farmerIds.isEmpty()) {
      jdbc.sql(
              "select farmer_id, latitude, longitude, municipality_id from property"
                  + " where deleted_at is null and farmer_id = any(?)")
          .param(farmerIds.toArray(UUID[]::new))
          .query((rs, i) ->
              places
                  .computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                  .add(
                      new Place(
                          toDouble(rs.getBigDecimal(2)),
                          toDouble(rs.getBigDecimal(3)),
                          (Integer) rs.getObject(4))))
          .list();
    }

    Map<UUID, UUID> committeeGroup = new HashMap<>();
    for (var m : committee.findByAssociationIdOrderByStartedOnDesc(associationId)) {
      if (m.isActiveOn(plannedOn)) {
        UUID id = m.getFarmerId() != null ? m.getFarmerId() : m.getCollaboratorId();
        committeeGroup.putIfAbsent(id, m.getRepresentedGroupId());
      }
    }

    Set<UUID> inConflict = new HashSet<>();
    for (MemberConflict c : conflicts.findInvolving(target.farmerId())) {
      inConflict.add(c.otherThan(target.farmerId()));
    }

    LocalDate agendaFrom = plannedOn.isBefore(today) ? plannedOn : today;
    LocalDate horizon = today.plusDays(AGENDA_DAYS);
    LocalDate agendaTo = plannedOn.isAfter(horizon) ? plannedOn : horizon;
    Map<UUID, Integer> plannedSoon = new HashMap<>();
    Set<UUID> sameDay = new HashSet<>();
    for (Object[] row : participants.plannedAgenda(associationId, agendaFrom, agendaTo)) {
      UUID person = row[0] != null ? (UUID) row[0] : (UUID) row[1];
      LocalDate day = (LocalDate) row[2];
      if (row[3].equals(ignoreVisitId)) {
        continue;
      }
      if (day.equals(plannedOn)) {
        sameDay.add(person);
      }
      if (!day.isBefore(today) && !day.isAfter(horizon)) {
        plannedSoon.merge(person, 1, Integer::sum);
      }
    }

    Set<UUID> visitedTarget = new HashSet<>();
    Set<UUID> visitedByTarget = new HashSet<>();
    for (Object[] row :
        participants.doneVisitorPairs(associationId, today.minusMonths(HISTORY_MONTHS))) {
      UUID visitor = (UUID) row[0];
      UUID property = (UUID) row[1];
      UUID owner = (UUID) row[2];
      if (property.equals(target.propertyId())) {
        visitedTarget.add(visitor);
      }
      if (visitor.equals(target.farmerId())) {
        visitedByTarget.add(owner);
      }
    }

    var result = new LinkedHashMap<UUID, Candidate>();
    for (var p : persons) {
      result.put(
          p.id(),
          new Candidate(
              p,
              active.getOrDefault(p.id(), false),
              groupOf.get(p.id()),
              committeeGroup.get(p.id()),
              places.getOrDefault(p.id(), List.of()),
              p.isFarmer() && inConflict.contains(p.id()),
              sameDay.contains(p.id()),
              visitedTarget.contains(p.id()),
              visitedByTarget.contains(p.id()),
              plannedSoon.getOrDefault(p.id(), 0)));
    }
    return result;
  }

  private static Double toDouble(java.math.BigDecimal d) {
    return d == null ? null : d.doubleValue();
  }
}
