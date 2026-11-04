// Calcula os números do painel inicial com consultas agregadas, sem uma consulta por linha (doc 1
// §3.11).
package br.edu.ifba.agrofacil.dashboard;

import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.catalog.UnmappedItemService;
import br.edu.ifba.agrofacil.certification.CertificationReader;
import br.edu.ifba.agrofacil.certification.CertificationStatus;
import br.edu.ifba.agrofacil.certification.CertificationStatusCalculator;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.dashboard.DashboardSummary.UpcomingExpiration;
import br.edu.ifba.agrofacil.dashboard.DashboardSummary.WeekCount;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordType;
import br.edu.ifba.agrofacil.spg.visit.VisitStatusService;
import java.sql.Date;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

  /** Janela de "registrou recentemente" (RF19). */
  private static final int RECENT_DAYS = 30;

  /** Próximos vencimentos: até 90 dias, no máximo 10 linhas. */
  private static final int UPCOMING_DAYS = 90;

  private static final int UPCOMING_LIMIT = 10;
  private static final int WEEKS = 12;

  /** Chave da contagem de propriedades sem certificação. */
  private static final String NO_CERTIFICATION = "NONE";

  private final JdbcClient jdbc;
  private final AssociationService associations;
  private final CertificationReader certifications;
  private final UnmappedItemService unmapped;
  private final VisitStatusService visits;
  private final Clock clock;

  public DashboardService(
      JdbcClient jdbc,
      AssociationService associations,
      CertificationReader certifications,
      UnmappedItemService unmapped,
      VisitStatusService visits,
      Clock clock) {
    this.jdbc = jdbc;
    this.associations = associations;
    this.certifications = certifications;
    this.unmapped = unmapped;
    this.visits = visits;
    this.clock = clock;
  }

  /** Números do painel para a associação do coordenador. */
  @Transactional(readOnly = true)
  public DashboardSummary summary(CurrentUser user) {
    UUID assoc = user.associationId();
    LocalDate today = certifications.today();
    var properties = properties(assoc);
    var certs = certificationsOf(properties, assoc, today);
    return new DashboardSummary(
        farmers(assoc, today),
        new DashboardSummary.Properties(
            properties.size(), properties.stream().filter(p -> !p.hasLocation()).count()),
        certs.counts(),
        certs.upcoming(),
        records(assoc, today),
        unmapped.count(user),
        visits.dashboardCounts(assoc),
        clock.instant());
  }

  private DashboardSummary.Farmers farmers(UUID assoc, LocalDate today) {
    return jdbc.sql(
            "select"
                + " count(*) filter (where u.status = 'ACTIVE') as active,"
                + " count(*) filter (where u.status = 'INACTIVE') as inactive,"
                + " count(*) filter (where u.status = 'ACTIVE' and not exists ("
                + "   select 1 from management_record r where r.farmer_id = u.id"
                + "   and r.deleted_at is null and r.performed_on >= ?)) as without_records,"
                + " count(*) filter (where u.status = 'ACTIVE' and not exists ("
                + "   select 1 from device d where d.user_id = u.id and d.revoked_at is null))"
                + "   as without_device"
                + " from app_user u where u.association_id = ? and u.role = 'FARMER'"
                + " and u.deleted_at is null")
        .params(Date.valueOf(today.minusDays(RECENT_DAYS)), assoc)
        .query((rs, i) ->
            new DashboardSummary.Farmers(
                rs.getLong("active"),
                rs.getLong("inactive"),
                rs.getLong("without_records"),
                rs.getLong("without_device")))
        .single();
  }

  private record PropertyRow(
      UUID id, String name, UUID farmerId, String farmerName, boolean hasLocation) {}

  private List<PropertyRow> properties(UUID assoc) {
    return jdbc.sql(
            "select p.id, p.name, p.farmer_id, u.name as farmer_name,"
                + " p.latitude is not null as has_location"
                + " from property p join app_user u on u.id = p.farmer_id"
                + " where p.association_id = ? and p.deleted_at is null")
        .param(assoc)
        .query((rs, i) ->
            new PropertyRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getObject("farmer_id", UUID.class),
                rs.getString("farmer_name"),
                rs.getBoolean("has_location")))
        .list();
  }

  private record CertificationNumbers(
      Map<String, Long> counts, List<UpcomingExpiration> upcoming) {}

  private CertificationNumbers certificationsOf(
      List<PropertyRow> properties, UUID assoc, LocalDate today) {
    var counts = new LinkedHashMap<String, Long>();
    for (var s : CertificationStatus.values()) {
      counts.put(s.name(), 0L);
    }
    counts.put(NO_CERTIFICATION, 0L);
    var current =
        certifications.currentByProperty(
            properties.stream().map(PropertyRow::id).toList(), associations.get(assoc));
    var upcoming = new ArrayList<UpcomingExpiration>();
    for (var p : properties) {
      var cert = current.get(p.id());
      String key = cert == null ? NO_CERTIFICATION : cert.status().name();
      counts.merge(key, 1L, Long::sum);
      if (cert != null
          && CertificationStatusCalculator.isInForce(cert.status())
          && cert.daysToExpire() <= UPCOMING_DAYS) {
        upcoming.add(
            new UpcomingExpiration(
                cert.id(),
                p.id(),
                p.name(),
                p.farmerId(),
                p.farmerName(),
                cert.expiresOn(),
                cert.daysToExpire()));
      }
    }
    upcoming.sort(
        Comparator.comparing(UpcomingExpiration::expiresOn)
            .thenComparing(UpcomingExpiration::propertyName));
    return new CertificationNumbers(
        counts, upcoming.subList(0, Math.min(UPCOMING_LIMIT, upcoming.size())));
  }

  private DashboardSummary.Records records(UUID assoc, LocalDate today) {
    LocalDate since = today.minusDays(RECENT_DAYS);
    var byType = new EnumMap<RecordType, Long>(RecordType.class);
    for (var t : RecordType.values()) {
      byType.put(t, 0L);
    }
    jdbc.sql(
            "select record_type, count(*) as n from management_record"
                + " where association_id = ? and deleted_at is null and performed_on >= ?"
                + " group by record_type")
        .params(assoc, Date.valueOf(since))
        .query((rs, i) -> Map.entry(RecordType.valueOf(rs.getString(1)), rs.getLong(2)))
        .list()
        .forEach(e -> byType.put(e.getKey(), e.getValue()));
    long last30 = byType.values().stream().mapToLong(Long::longValue).sum();

    long incomplete =
        jdbc.sql(
                "select count(*) from management_record where association_id = ?"
                    + " and deleted_at is null and completeness = 'INCOMPLETE'")
            .param(assoc)
            .query(Long.class)
            .single();
    long openCorrections =
        jdbc.sql(
                "select count(*) from correction_request c join management_record r"
                    + " on r.id = c.record_id where r.association_id = ? and c.status = 'OPEN'"
                    + " and r.deleted_at is null")
            .param(assoc)
            .query(Long.class)
            .single();
    return new DashboardSummary.Records(
        last30, byType, incomplete, openCorrections, perWeek(assoc, today));
  }

  /** Semanas começando na segunda-feira, incluindo as sem registro (contagem zero). */
  private List<WeekCount> perWeek(UUID assoc, LocalDate today) {
    LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate first = thisWeek.minusWeeks(WEEKS - 1L);
    var counts = new HashMap<LocalDate, Long>();
    jdbc.sql(
            "select date_trunc('week', performed_on)::date as week, count(*) as n"
                + " from management_record where association_id = ? and deleted_at is null"
                + " and performed_on >= ? group by 1")
        .params(assoc, Date.valueOf(first))
        .query((rs, i) -> Map.entry(rs.getObject(1, Date.class).toLocalDate(), rs.getLong(2)))
        .list()
        .forEach(e -> counts.put(e.getKey(), e.getValue()));
    var result = new ArrayList<WeekCount>();
    for (int w = 0; w < WEEKS; w++) {
      LocalDate start = first.plusWeeks(w);
      result.add(new WeekCount(start, counts.getOrDefault(start, 0L)));
    }
    return result;
  }
}
