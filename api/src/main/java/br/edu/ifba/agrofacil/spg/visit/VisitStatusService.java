// Monta a situação das visitas por propriedade com poucas consultas agregadas (RN29, doc 2
// §1.15.1).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.association.Association;
import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.certification.Certification;
import br.edu.ifba.agrofacil.certification.CertificationReader;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.IdName;
import br.edu.ifba.agrofacil.dashboard.DashboardSummary;
import br.edu.ifba.agrofacil.property.PropertyService;
import br.edu.ifba.agrofacil.spg.visit.VisitCycleCalculator.CertificationDates;
import br.edu.ifba.agrofacil.spg.visit.VisitCycleCalculator.State;
import br.edu.ifba.agrofacil.spg.visit.VisitCycleCalculator.VisitDone;
import br.edu.ifba.agrofacil.spg.visit.VisitCycleCalculator.VisitSettings;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitStatusService {

  /** RN01: grupo com menos agricultores ativos que isso gera aviso. */
  public static final int MIN_GROUP_SIZE = 3;

  /** Toda propriedade precisa de 1 visita de verificação por ciclo (RN29). */
  private static final int VERIFICATIONS_REQUIRED = 1;

  /** Janela das visitas marcadas mostradas no painel inicial. */
  private static final int PLANNED_WINDOW_DAYS = 30;

  public static final String NO_LOCATION_WARNING =
      "Sem localização: a distância é estimada pelo município";
  public static final String SMALL_GROUP_WARNING = "Grupo com menos de 3 agricultores";
  public static final String NO_GROUP_WARNING = "Agricultor sem grupo do SPG";

  private final JdbcClient jdbc;
  private final AssociationService associations;
  private final CertificationReader certifications;
  private final PropertyService properties;

  public VisitStatusService(
      JdbcClient jdbc,
      AssociationService associations,
      CertificationReader certifications,
      PropertyService properties) {
    this.jdbc = jdbc;
    this.associations = associations;
    this.certifications = certifications;
    this.properties = properties;
  }

  /** Situação de uma propriedade (consultada antes de renovar a certificação, RN39). */
  @Transactional(readOnly = true)
  public PropertyVisitStatus forProperty(CurrentUser user, UUID propertyId) {
    properties.findVisible(user, propertyId);
    return compute(user.associationId(), propertyId).getFirst();
  }

  /** Situação de uma propriedade sem conferir escopo (uso interno: rotinas e documentos). */
  @Transactional(readOnly = true)
  public PropertyVisitStatus statusOf(UUID associationId, UUID propertyId) {
    var list = compute(associationId, propertyId);
    return list.isEmpty() ? null : list.getFirst();
  }

  /**
   * O que ainda falta no ciclo, para o texto do alerta de vencimento (doc 1 §6.3), ex.: "Falta a
   * visita da comissão."; vazio se não falta nada.
   */
  public static String missingText(PropertyVisitStatus st) {
    if (st == null) {
      return "";
    }
    var parts = new ArrayList<String>();
    long peers = st.peer().required() - st.peer().done();
    if (peers > 0) {
      parts.add(
          peers == 1 ? "Falta 1 visita de pares." : "Faltam %d visitas de pares.".formatted(peers));
    }
    if (st.verification().done() < st.verification().required()) {
      parts.add("Falta a visita da comissão.");
    }
    return String.join(" ", parts);
  }

  /**
   * Planejamento: todas as propriedades ativas da associação. Ordem: URGENT, depois as mais perto
   * do vencimento.
   */
  @Transactional(readOnly = true)
  public List<PropertyVisitStatus> all(UUID associationId) {
    var list = new ArrayList<>(compute(associationId, null));
    list.sort(
        Comparator.comparing((PropertyVisitStatus s) -> s.state() != State.URGENT)
            .thenComparing(s -> s.daysToExpire() == null ? Long.MAX_VALUE : s.daysToExpire())
            .thenComparing(s -> s.property().name()));
    return list;
  }

  /** Números das visitas para o painel inicial (doc 1 §3.11). */
  @Transactional(readOnly = true)
  public DashboardSummary.Visits dashboardCounts(UUID associationId) {
    var statuses = compute(associationId, null);
    LocalDate today = certifications.today();
    return jdbc.sql(
            "select"
                + " count(*) filter (where planned_on between ? and ?) as planned_soon,"
                + " count(*) filter (where planned_on < ?) as result_missing"
                + " from visit where association_id = ? and status = 'PLANNED'"
                + " and deleted_at is null")
        .params(
            Date.valueOf(today),
            Date.valueOf(today.plusDays(PLANNED_WINDOW_DAYS)),
            Date.valueOf(today),
            associationId)
        .query((rs, i) ->
            new DashboardSummary.Visits(
                statuses.stream().filter(PropertyVisitStatus::windowOpen).count(),
                statuses.stream().filter(s -> s.state() == State.URGENT).count(),
                rs.getLong("planned_soon"),
                rs.getLong("result_missing")))
        .single();
  }

  private record PropertyRow(
      UUID id, String name, UUID farmerId, String farmerName, boolean hasLocation, UUID groupId) {}

  /**
   * @param onlyPropertyId nulo = todas as propriedades ativas da associação
   */
  private List<PropertyVisitStatus> compute(UUID associationId, UUID onlyPropertyId) {
    Association association = associations.get(associationId);
    LocalDate today = certifications.today();
    var rows =
        jdbc.sql(
                "select p.id, p.name, p.farmer_id, u.name as farmer_name,"
                    + " p.latitude is not null as has_location, f.group_id"
                    + " from property p join app_user u on u.id = p.farmer_id"
                    + " left join farmer f on f.user_id = p.farmer_id"
                    + " where p.association_id = ? and p.deleted_at is null"
                    + " and (cast(? as uuid) is null or p.id = ?)")
            .params(associationId, onlyPropertyId, onlyPropertyId)
            .query((rs, i) ->
                new PropertyRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getObject("farmer_id", UUID.class),
                    rs.getString("farmer_name"),
                    rs.getBoolean("has_location"),
                    rs.getObject("group_id", UUID.class)))
            .list();
    if (rows.isEmpty()) {
      return List.of();
    }
    var ids = rows.stream().map(PropertyRow::id).toList();
    Map<UUID, Certification> current = certifications.currentEntities(ids);
    Map<UUID, List<VisitDone>> done = doneVisits(associationId);
    Map<UUID, Map<VisitType, Long>> planned = plannedCounts(associationId);
    Map<UUID, IdName> groups = groupNames(associationId);
    Map<UUID, Long> groupSizes = activeFarmersByGroup(associationId);
    var settings =
        new VisitSettings(association.getVisitWindowDays(), association.getPeerVisitsPerYear());

    var result = new ArrayList<PropertyVisitStatus>();
    for (var row : rows) {
      var cert = current.get(row.id());
      var dates =
          cert == null ? null : new CertificationDates(cert.getIssuedOn(), cert.getExpiresOn());
      var r =
          VisitCycleCalculator.calculate(
              dates, done.getOrDefault(row.id(), List.of()), settings, today);
      var plannedOf = planned.getOrDefault(row.id(), Map.of());
      var warnings = new ArrayList<String>();
      if (!row.hasLocation()) {
        warnings.add(NO_LOCATION_WARNING);
      }
      if (row.groupId() == null) {
        warnings.add(NO_GROUP_WARNING);
      } else if (groupSizes.getOrDefault(row.groupId(), 0L) < MIN_GROUP_SIZE) {
        warnings.add(SMALL_GROUP_WARNING);
      }
      result.add(
          new PropertyVisitStatus(
              new PropertyVisitStatus.PropertyRef(
                  row.id(), row.name(), row.farmerId(), row.farmerName()),
              row.groupId() == null ? null : groups.get(row.groupId()),
              new PropertyVisitStatus.Cycle(r.from(), r.to(), cert == null ? null : cert.getId()),
              r.windowOpensOn(),
              r.windowOpen(),
              r.daysToExpire(),
              new PropertyVisitStatus.Count(
                  settings.peerVisitsPerYear(),
                  r.peerDone(),
                  plannedOf.getOrDefault(VisitType.PEER, 0L)),
              new PropertyVisitStatus.Count(
                  VERIFICATIONS_REQUIRED,
                  r.verificationDone(),
                  plannedOf.getOrDefault(VisitType.VERIFICATION, 0L)),
              r.state(),
              warnings));
    }
    return result;
  }

  private Map<UUID, List<VisitDone>> doneVisits(UUID associationId) {
    var result = new HashMap<UUID, List<VisitDone>>();
    jdbc.sql(
            "select property_id, type, done_on from visit where association_id = ?"
                + " and status = 'DONE' and deleted_at is null")
        .param(associationId)
        .query((rs, i) -> {
          result
              .computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
              .add(
                  new VisitDone(
                      VisitType.valueOf(rs.getString(2)),
                      rs.getObject(3, Date.class).toLocalDate()));
          return null;
        })
        .list();
    return result;
  }

  private Map<UUID, Map<VisitType, Long>> plannedCounts(UUID associationId) {
    var result = new HashMap<UUID, Map<VisitType, Long>>();
    jdbc.sql(
            "select property_id, type, count(*) from visit where association_id = ?"
                + " and status = 'PLANNED' and deleted_at is null group by 1, 2")
        .param(associationId)
        .query((rs, i) -> {
          result
              .computeIfAbsent(rs.getObject(1, UUID.class), k -> new HashMap<>())
              .put(VisitType.valueOf(rs.getString(2)), rs.getLong(3));
          return null;
        })
        .list();
    return result;
  }

  private Map<UUID, IdName> groupNames(UUID associationId) {
    var result = new HashMap<UUID, IdName>();
    jdbc.sql("select id, name from spg_group where association_id = ?")
        .param(associationId)
        .query((rs, i) -> new IdName(rs.getObject(1, UUID.class), rs.getString(2)))
        .list()
        .forEach(g -> result.put(g.id(), g));
    return result;
  }

  /** Agricultores ativos por grupo (RN01). */
  public Map<UUID, Long> activeFarmersByGroup(UUID associationId) {
    var result = new HashMap<UUID, Long>();
    jdbc.sql(
            "select f.group_id, count(*) from farmer f join app_user u on u.id = f.user_id"
                + " where u.association_id = ? and u.status = 'ACTIVE' and u.deleted_at is null"
                + " and f.group_id is not null group by 1")
        .param(associationId)
        .query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getLong(2)))
        .list()
        .forEach(e -> result.put(e.getKey(), e.getValue()));
    return result;
  }
}
