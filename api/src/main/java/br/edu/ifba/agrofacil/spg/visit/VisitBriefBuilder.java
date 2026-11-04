// Monta o resumo da visita com consultas agregadas sobre os registros dos últimos 12 meses (RN36).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.catalog.MunicipalityNames;
import br.edu.ifba.agrofacil.certification.CertificationReader;
import br.edu.ifba.agrofacil.common.domain.AddressValue;
import br.edu.ifba.agrofacil.common.domain.AreaValue;
import br.edu.ifba.agrofacil.common.domain.Decimals;
import br.edu.ifba.agrofacil.common.domain.GeoPointValue;
import br.edu.ifba.agrofacil.common.domain.Quantity;
import br.edu.ifba.agrofacil.property.PlotRepository;
import br.edu.ifba.agrofacil.property.PlotView;
import br.edu.ifba.agrofacil.property.PropertyRepository;
import br.edu.ifba.agrofacil.user.UserRepository;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Os nomes já vêm resolvidos, incluindo o texto de "Outro" (doc 2 §1.15.3). */
@Component
public class VisitBriefBuilder {

  private static final int MONTHS_BACK = 12;

  /** Nome do item: o do catálogo ou o texto livre. */
  private static final String CROP_NAME = "coalesce(c.name, trim(%s))";

  private final JdbcClient jdbc;
  private final PropertyRepository properties;
  private final PlotRepository plots;
  private final UserRepository users;
  private final MunicipalityNames municipalities;
  private final CertificationReader certifications;
  private final VisitRepository visits;

  public VisitBriefBuilder(
      JdbcClient jdbc,
      PropertyRepository properties,
      PlotRepository plots,
      UserRepository users,
      MunicipalityNames municipalities,
      CertificationReader certifications,
      VisitRepository visits) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.plots = plots;
    this.users = users;
    this.municipalities = municipalities;
    this.certifications = certifications;
    this.visits = visits;
  }

  /** Resumo da propriedade na data, sem {@code generatedAt}. */
  @Transactional(readOnly = true)
  public VisitBrief build(UUID propertyId, LocalDate today) {
    var property = properties.findById(propertyId).orElseThrow();
    var farmer = users.findById(property.getFarmerId()).orElseThrow();
    var address = AddressValue.toDto(property.getAddress(), municipalities::nameOf);
    Date since = Date.valueOf(today.minusMonths(MONTHS_BACK));
    return new VisitBrief(
        null,
        new VisitBrief.Farmer(farmer.getName(), farmer.getPhone()),
        new VisitBrief.PropertyInfo(
            property.getName(),
            address == null ? null : address.municipalityName(),
            address == null ? null : address.locality(),
            property.getAccessDirections(),
            GeoPointValue.toDto(property.getLocation(), null),
            AreaValue.toDto(property.getTotalArea())),
        plots.findByPropertyIdAndDeletedAtIsNullOrderByName(propertyId).stream()
            .map(PlotView::of)
            .map(p -> new VisitBrief.PlotInfo(p.name(), p.status(), p.area(), p.conversionEndsOn()))
            .toList(),
        activePlantings(propertyId),
        inputs(propertyId, since),
        purchases(propertyId, since),
        harvests(propertyId, since),
        outflows(propertyId, since),
        jdbc.sql(
                "select count(*) from management_record where property_id = ?"
                    + " and deleted_at is null and completeness = 'INCOMPLETE'")
            .param(propertyId)
            .query(Long.class)
            .single(),
        jdbc.sql(
                "select max(performed_on) from management_record where property_id = ?"
                    + " and deleted_at is null")
            .param(propertyId)
            .query(Date.class)
            .optional()
            .map(Date::toLocalDate)
            .orElse(null),
        certification(propertyId),
        previousVisit(propertyId));
  }

  private List<VisitBrief.ActivePlanting> activePlantings(UUID propertyId) {
    return jdbc.sql(
            "select "
                + CROP_NAME.formatted("pl.crop_other_name")
                + " as crop, coalesce(p.name, '') as plot, r.performed_on, pl.expected_harvest_on"
                + " from planting pl join management_record r on r.id = pl.record_id"
                + " left join crop c on c.id = pl.crop_id left join plot p on p.id = r.plot_id"
                + " where r.property_id = ? and r.deleted_at is null and pl.cycle_status = 'ACTIVE'"
                + " order by r.performed_on")
        .param(propertyId)
        .query((rs, i) ->
            new VisitBrief.ActivePlanting(
                rs.getString(1),
                rs.getString(2),
                rs.getObject(3, Date.class).toLocalDate(),
                toLocalDate(rs.getObject(4, Date.class))))
        .list();
  }

  private List<VisitBrief.InputUse> inputs(UUID propertyId, Date since) {
    return jdbc.sql(
            "select coalesce(f.name, trim(a.input_other_name)) as name, count(*),"
                + " max(r.performed_on), coalesce(bool_or(f.needs_authorization), false)"
                + " from input_application a join management_record r on r.id = a.record_id"
                + " left join farm_input f on f.id = a.input_id"
                + " where r.property_id = ? and r.deleted_at is null and r.performed_on >= ?"
                + " group by 1 order by 2 desc, 1")
        .params(propertyId, since)
        .query((rs, i) ->
            new VisitBrief.InputUse(
                rs.getString(1),
                rs.getLong(2),
                rs.getObject(3, Date.class).toLocalDate(),
                rs.getBoolean(4)))
        .list();
  }

  private List<VisitBrief.Purchase> purchases(UUID propertyId, Date since) {
    return jdbc.sql(
            "select coalesce(f.name, trim(pu.input_other_name)), pu.supplier, r.performed_on"
                + " from input_purchase pu join management_record r on r.id = pu.record_id"
                + " left join farm_input f on f.id = pu.input_id"
                + " where r.property_id = ? and r.deleted_at is null and r.performed_on >= ?"
                + " order by r.performed_on desc")
        .params(propertyId, since)
        .query((rs, i) ->
            new VisitBrief.Purchase(
                rs.getString(1), rs.getString(2), rs.getObject(3, Date.class).toLocalDate()))
        .list();
  }

  private List<VisitBrief.HarvestTotal> harvests(UUID propertyId, Date since) {
    var byCrop = new LinkedHashMap<String, List<Quantity>>();
    jdbc.sql(
            "select "
                + CROP_NAME.formatted("pl.crop_other_name")
                + " as crop, h.quantity_unit, sum(h.quantity)"
                + " from harvest h join management_record r on r.id = h.record_id"
                + " join planting pl on pl.record_id = h.planting_id"
                + " left join crop c on c.id = pl.crop_id"
                + " where r.property_id = ? and r.deleted_at is null and r.performed_on >= ?"
                + " and h.quantity is not null group by 1, 2 order by 1, 2")
        .params(propertyId, since)
        .query((rs, i) -> {
          byCrop
              .computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
              .add(new Quantity(Decimals.round(rs.getBigDecimal(3)), rs.getString(2)));
          return null;
        })
        .list();
    return byCrop.entrySet().stream()
        .map(e -> new VisitBrief.HarvestTotal(e.getKey(), e.getValue()))
        .toList();
  }

  private List<VisitBrief.OutflowTotal> outflows(UUID propertyId, Date since) {
    record Key(String product, String destination) {}
    var byKey = new LinkedHashMap<Key, List<Quantity>>();
    jdbc.sql(
            "select "
                + CROP_NAME.formatted("o.product_other_name")
                + " as product, o.destination, o.quantity_unit, sum(o.quantity)"
                + " from outflow o join management_record r on r.id = o.record_id"
                + " left join crop c on c.id = o.crop_id"
                + " where r.property_id = ? and r.deleted_at is null and r.performed_on >= ?"
                + " group by 1, 2, 3 order by 1, 2, 3")
        .params(propertyId, since)
        .query((rs, i) -> {
          var totals =
              byKey.computeIfAbsent(
                  new Key(rs.getString(1), rs.getString(2)), k -> new ArrayList<>());
          BigDecimal sum = rs.getBigDecimal(4);
          if (sum != null) {
            totals.add(new Quantity(Decimals.round(sum), rs.getString(3)));
          }
          return null;
        })
        .list();
    return byKey.entrySet().stream()
        .map(e ->
            new VisitBrief.OutflowTotal(
                e.getKey().product(), e.getKey().destination(), e.getValue()))
        .toList();
  }

  private VisitBrief.CertificationInfo certification(UUID propertyId) {
    var cert = certifications.currentEntities(List.of(propertyId)).get(propertyId);
    if (cert == null) {
      return null;
    }
    var products =
        jdbc.sql(
                "select coalesce(c.name, cp.product_name) from certification_product cp"
                    + " left join crop c on c.id = cp.crop_id where cp.certification_id = ?"
                    + " order by 1")
            .param(cert.getId())
            .query(String.class)
            .list();
    return new VisitBrief.CertificationInfo(cert.getNumber(), cert.getExpiresOn(), products);
  }

  private VisitBrief.PreviousVisit previousVisit(UUID propertyId) {
    return visits
        .findByPropertyIdAndStatusAndDeletedAtIsNullOrderByDoneOnDesc(propertyId, VisitStatus.DONE)
        .stream()
        .findFirst()
        .map(v ->
            new VisitBrief.PreviousVisit(
                v.getType(), v.getDoneOn(), v.getIssuesFound(), v.getRecommendations()))
        .orElse(null);
  }

  private static LocalDate toLocalDate(Date d) {
    return d == null ? null : d.toLocalDate();
  }
}
