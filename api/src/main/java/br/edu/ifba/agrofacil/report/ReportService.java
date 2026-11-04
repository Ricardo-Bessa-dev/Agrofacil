// Gera os relatórios do contrato em PDF e CSV: caderno de campo, agricultores, registros e
// certificações (doc 1 §3.14).
package br.edu.ifba.agrofacil.report;

import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.catalog.MeasurementUnit;
import br.edu.ifba.agrofacil.catalog.MeasurementUnitRepository;
import br.edu.ifba.agrofacil.certification.CertificationReader;
import br.edu.ifba.agrofacil.certification.CertificationRepository;
import br.edu.ifba.agrofacil.certification.CertificationView;
import br.edu.ifba.agrofacil.common.domain.BrFormat;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.pdf.PdfRenderer;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.config.AppProperties;
import br.edu.ifba.agrofacil.property.PlotRepository;
import br.edu.ifba.agrofacil.property.PlotView;
import br.edu.ifba.agrofacil.property.PropertyService;
import br.edu.ifba.agrofacil.record.Harvest;
import br.edu.ifba.agrofacil.record.InputApplication;
import br.edu.ifba.agrofacil.record.RecordEnums.Completeness;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordType;
import br.edu.ifba.agrofacil.record.RecordFilter;
import br.edu.ifba.agrofacil.record.RecordListItem;
import br.edu.ifba.agrofacil.record.RecordQueryService;
import br.edu.ifba.agrofacil.record.RecordRepository;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

  /** Período padrão do caderno de campo: os últimos 12 meses. */
  private static final int DEFAULT_PERIOD_MONTHS = 12;

  /** Janela de "registros recentes" do relatório de agricultores. */
  private static final int RECENT_RECORDS_DAYS = 90;

  private static final DateTimeFormatter GENERATED_AT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

  private final PropertyService properties;
  private final PlotRepository plots;
  private final CertificationReader certificationReader;
  private final CertificationRepository certifications;
  private final AssociationService associations;
  private final RecordQueryService recordQueries;
  private final RecordRepository records;
  private final MeasurementUnitRepository units;
  private final JdbcClient jdbc;
  private final PdfRenderer pdf;
  private final Clock clock;
  private final AppProperties props;

  public ReportService(
      PropertyService properties,
      PlotRepository plots,
      CertificationReader certificationReader,
      CertificationRepository certifications,
      AssociationService associations,
      RecordQueryService recordQueries,
      RecordRepository records,
      MeasurementUnitRepository units,
      JdbcClient jdbc,
      PdfRenderer pdf,
      Clock clock,
      AppProperties props) {
    this.properties = properties;
    this.plots = plots;
    this.certificationReader = certificationReader;
    this.certifications = certifications;
    this.associations = associations;
    this.recordQueries = recordQueries;
    this.records = records;
    this.units = units;
    this.jdbc = jdbc;
    this.pdf = pdf;
    this.clock = clock;
    this.props = props;
  }

  /**
   * Caderno de campo de uma propriedade no período. O agricultor só gera o da própria propriedade
   * (o escopo responde 404 para as outras).
   */
  @Transactional(readOnly = true)
  public ReportFile fieldNotebook(
      CurrentUser user, UUID propertyId, LocalDate from, LocalDate to, ReportFormat format) {
    var property = properties.findVisible(user, propertyId);
    LocalDate today = certificationReader.today();
    LocalDate end = to != null ? to : today;
    LocalDate start = from != null ? from : end.minusMonths(DEFAULT_PERIOD_MONTHS);
    if (start.isAfter(end)) {
      throw ApiException.validation("from", "AFTER_TO", "A data inicial é depois da final.");
    }
    var items =
        recordQueries.listAll(
            user,
            new RecordFilter(
                null, propertyId, null, null, null, start, end, null, null, null, null));
    String baseName =
        "caderno-de-campo-" + ReportFile.slug(property.getName()) + "-" + start + "-a-" + end;
    var unitNames = unitsByCode();
    if (format == ReportFormat.CSV) {
      var csv =
          new CsvWriter()
              .row("Nº", "Data", "Tipo", "Área", "Item", "Quantidade", "Tamanho", "Completo");
      for (var r : items) {
        csv.row(
            r.number(),
            r.performedOn(),
            Labels.recordType(r.type()),
            r.plot() == null ? "" : r.plot().name(),
            r.subject(),
            Labels.quantity(r.quantity(), unitNames),
            Labels.area(r.area()),
            r.completeness() == Completeness.COMPLETE);
      }
      return new ReportFile(baseName + ".csv", ReportFile.CSV, csv.toBytes());
    }
    var view = properties.views(List.of(property), property.getAssociationId()).getFirst();
    var association = associations.get(property.getAssociationId());
    var current = certificationReader.currentEntities(List.of(propertyId)).get(propertyId);
    FieldNotebookData.CertificationLine certLine = null;
    if (current != null) {
      var summary = certificationReader.summary(current, association);
      certLine =
          new FieldNotebookData.CertificationLine(
              current.getNumber() == null ? "—" : current.getNumber(),
              BrFormat.date(current.getIssuedOn()),
              BrFormat.date(current.getExpiresOn()),
              Labels.certificationStatus(summary.status()));
    }
    var data =
        new FieldNotebookData(
            association.getName(),
            association.getOpacName(),
            view.farmerName(),
            property.getName(),
            view.address() == null ? "" : nullToEmpty(view.address().municipalityName()),
            Labels.area(view.totalArea()),
            BrFormat.date(start) + " a " + BrFormat.date(end),
            plots.findByPropertyIdAndDeletedAtIsNullOrderByName(propertyId).stream()
                .map(PlotView::of)
                .map(p ->
                    new FieldNotebookData.PlotLine(
                        p.name(), Labels.plotStatus(p.status()), Labels.area(p.area())))
                .toList(),
            certLine,
            plantingBlocks(items, unitNames),
            linesOf(items, RecordType.INPUT_APPLICATION, unitNames, withoutPlanting(items)),
            linesOf(items, RecordType.INPUT_PURCHASE, unitNames, r -> true),
            linesOf(items, RecordType.OUTFLOW, unitNames, r -> true),
            generatedAt());
    return new ReportFile(
        baseName + ".pdf",
        ReportFile.PDF,
        pdf.render("report/field-notebook", Map.of("data", data)));
  }

  /** Agricultores, grupo, propriedades, certificação e registros dos últimos 90 dias. */
  @Transactional(readOnly = true)
  public ReportFile members(CurrentUser user, ReportFormat format) {
    var association = associations.get(user.associationId());
    LocalDate since = certificationReader.today().minusDays(RECENT_RECORDS_DAYS);
    record Row(
        String farmer,
        String status,
        String group,
        UUID propertyId,
        String property,
        long recent) {}
    var rows =
        jdbc.sql(
                "select u.name as farmer, u.status, g.name as grp, p.id as property_id,"
                    + " p.name as property, (select count(*) from management_record r"
                    + "   where r.property_id = p.id and r.deleted_at is null"
                    + "   and r.performed_on >= ?) as recent"
                    + " from app_user u left join farmer f on f.user_id = u.id"
                    + " left join spg_group g on g.id = f.group_id"
                    + " left join property p on p.farmer_id = u.id and p.deleted_at is null"
                    + " where u.association_id = ? and u.role = 'FARMER' and u.deleted_at is null"
                    + " order by u.name, p.name")
            .params(Date.valueOf(since), user.associationId())
            .query((rs, i) ->
                new Row(
                    rs.getString("farmer"),
                    "ACTIVE".equals(rs.getString("status")) ? "Ativo" : "Inativo",
                    nullToEmpty(rs.getString("grp")),
                    rs.getObject("property_id", UUID.class),
                    nullToEmpty(rs.getString("property")),
                    rs.getLong("recent")))
            .list();
    var current =
        certificationReader.currentByProperty(
            rows.stream().map(Row::propertyId).filter(Objects::nonNull).toList(), association);
    var headers =
        List.of(
            "Agricultor",
            "Situação",
            "Grupo",
            "Propriedade",
            "Certificação",
            "Vencimento",
            "Registros (90 dias)");
    var table = new ArrayList<List<?>>();
    for (var r : rows) {
      var cert = r.propertyId() == null ? null : current.get(r.propertyId());
      table.add(
          List.of(
              r.farmer(),
              r.status(),
              r.group(),
              r.property(),
              r.propertyId() == null
                  ? ""
                  : Labels.certificationStatus(cert == null ? null : cert.status()),
              cert == null ? "" : BrFormat.date(cert.expiresOn()),
              r.propertyId() == null ? "" : Long.toString(r.recent())));
    }
    return table(
        "agricultores",
        "Agricultores e propriedades",
        association.getName(),
        headers,
        table,
        format);
  }

  /** Certificações atuais de cada propriedade, das que vencem antes para as que vencem depois. */
  @Transactional(readOnly = true)
  public ReportFile certificationsReport(CurrentUser user, ReportFormat format) {
    var association = associations.get(user.associationId());
    var views =
        certificationReader
            .views(certifications.findAliveInAssociation(user.associationId()), association)
            .stream()
            .filter(CertificationView::isCurrent)
            .sorted(Comparator.comparing(CertificationView::expiresOn))
            .toList();
    var headers =
        List.of(
            "Agricultor",
            "Propriedade",
            "Número",
            "Emissão",
            "Vencimento",
            "Situação",
            "Dias para vencer");
    var table = new ArrayList<List<?>>();
    for (var v : views) {
      table.add(
          List.of(
              v.property() == null ? "" : nullToEmpty(v.property().farmerName()),
              v.property() == null ? "" : v.property().name(),
              nullToEmpty(v.number()),
              BrFormat.date(v.issuedOn()),
              BrFormat.date(v.expiresOn()),
              Labels.certificationStatus(v.status()),
              Long.toString(v.daysToExpire())));
    }
    return table(
        "certificacoes",
        "Certificações e vencimentos",
        association.getName(),
        headers,
        table,
        format);
  }

  /** Exportação bruta dos registros com os mesmos filtros da lista do painel (só CSV). */
  @Transactional(readOnly = true)
  public ReportFile recordsCsv(CurrentUser user, RecordFilter filter) {
    var unitNames = unitsByCode();
    var csv =
        new CsvWriter()
            .row(
                "Nº",
                "Data",
                "Tipo",
                "Agricultor",
                "Propriedade",
                "Área",
                "Item",
                "Quantidade",
                "Unidade",
                "Tamanho (m²)",
                "Completo",
                "Origem");
    for (var r : recordQueries.listAll(user, filter)) {
      var unit = r.quantity() == null ? null : unitNames.get(r.quantity().unit());
      csv.row(
          r.number(),
          r.performedOn(),
          Labels.recordType(r.type()),
          r.farmer() == null ? "" : r.farmer().name(),
          r.property() == null ? "" : r.property().name(),
          r.plot() == null ? "" : r.plot().name(),
          r.subject(),
          r.quantity() == null ? null : r.quantity().value(),
          unit == null ? (r.quantity() == null ? "" : r.quantity().unit()) : unit.getNamePlural(),
          r.area() == null ? null : r.area().m2(),
          r.completeness() == Completeness.COMPLETE,
          r.source() == null ? "" : r.source().name());
    }
    return new ReportFile(
        "registros-" + certificationReader.today() + ".csv", ReportFile.CSV, csv.toBytes());
  }

  /** Tabela simples em PDF ou CSV, com o mesmo cabeçalho e as mesmas linhas. */
  private ReportFile table(
      String baseName,
      String title,
      String subtitle,
      List<String> headers,
      List<List<?>> rows,
      ReportFormat format) {
    String name = baseName + "-" + certificationReader.today();
    if (format == ReportFormat.CSV) {
      var csv = new CsvWriter().row(headers);
      rows.forEach(csv::row);
      return new ReportFile(name + ".csv", ReportFile.CSV, csv.toBytes());
    }
    var model = new HashMap<String, Object>();
    model.put("title", title);
    model.put("subtitle", subtitle);
    model.put("headers", headers);
    model.put("rows", rows);
    model.put("generatedAt", generatedAt());
    return new ReportFile(name + ".pdf", ReportFile.PDF, pdf.render("report/table", model));
  }

  /** Plantios do período, cada um com as aplicações e colheitas ligadas a ele. */
  private List<FieldNotebookData.PlantingBlock> plantingBlocks(
      List<RecordListItem> items, Map<String, MeasurementUnit> unitNames) {
    var plantingOf = plantingIds(items);
    var blocks = new LinkedHashMap<UUID, FieldNotebookData.PlantingBlock>();
    for (var r : items) {
      if (r.type() == RecordType.PLANTING) {
        blocks.put(
            r.id(),
            new FieldNotebookData.PlantingBlock(
                line(r, unitNames), new ArrayList<>(), new ArrayList<>()));
      }
    }
    for (var r : items) {
      var block = blocks.get(plantingOf.get(r.id()));
      if (block == null) {
        continue;
      }
      if (r.type() == RecordType.INPUT_APPLICATION) {
        block.applications().add(line(r, unitNames));
      } else if (r.type() == RecordType.HARVEST) {
        block.harvests().add(line(r, unitNames));
      }
    }
    return List.copyOf(blocks.values());
  }

  /** Aplicações sem plantio, ou cujo plantio ficou fora do período. */
  private Predicate<RecordListItem> withoutPlanting(List<RecordListItem> items) {
    var plantingOf = plantingIds(items);
    var plantingsInPeriod =
        items.stream()
            .filter(r -> r.type() == RecordType.PLANTING)
            .map(RecordListItem::id)
            .collect(Collectors.toSet());
    return r -> !plantingsInPeriod.contains(plantingOf.get(r.id()));
  }

  private List<FieldNotebookData.Line> linesOf(
      List<RecordListItem> items,
      RecordType type,
      Map<String, MeasurementUnit> unitNames,
      Predicate<RecordListItem> filter) {
    return items.stream()
        .filter(r -> r.type() == type)
        .filter(filter)
        .map(r -> line(r, unitNames))
        .toList();
  }

  /** Plantio de cada aplicação e colheita (as compras e saídas não têm). */
  private Map<UUID, UUID> plantingIds(List<RecordListItem> items) {
    var ids =
        items.stream()
            .filter(r -> r.type() == RecordType.INPUT_APPLICATION || r.type() == RecordType.HARVEST)
            .map(RecordListItem::id)
            .toList();
    var result = new HashMap<UUID, UUID>();
    for (var r : records.findByIdIn(ids)) {
      UUID planting =
          switch (r) {
            case InputApplication a -> a.getPlantingId();
            case Harvest h -> h.getPlantingId();
            default -> null;
          };
      if (planting != null) {
        result.put(r.getId(), planting);
      }
    }
    return result;
  }

  private static FieldNotebookData.Line line(
      RecordListItem r, Map<String, MeasurementUnit> unitNames) {
    String detail =
        r.quantity() != null ? Labels.quantity(r.quantity(), unitNames) : Labels.area(r.area());
    return new FieldNotebookData.Line(
        BrFormat.date(r.performedOn()),
        r.number() == null ? "" : r.number().toString(),
        r.plot() == null ? "" : r.plot().name(),
        nullToEmpty(r.subject()),
        detail);
  }

  private Map<String, MeasurementUnit> unitsByCode() {
    return units.findAllByOrderBySortOrder().stream()
        .collect(Collectors.toMap(MeasurementUnit::getCode, Function.identity()));
  }

  private String generatedAt() {
    return GENERATED_AT.format(clock.instant().atZone(props.zone()));
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
