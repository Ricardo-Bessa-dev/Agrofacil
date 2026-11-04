// Monta os dados de cada documento do SPG, confere as pré-condições e junta os avisos (RN38).
package br.edu.ifba.agrofacil.document;

import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.catalog.Crop;
import br.edu.ifba.agrofacil.catalog.CropRepository;
import br.edu.ifba.agrofacil.catalog.MeasurementUnit;
import br.edu.ifba.agrofacil.catalog.MeasurementUnitRepository;
import br.edu.ifba.agrofacil.catalog.MunicipalityNames;
import br.edu.ifba.agrofacil.certification.Certification;
import br.edu.ifba.agrofacil.certification.CertificationReader;
import br.edu.ifba.agrofacil.certification.CertificationRepository;
import br.edu.ifba.agrofacil.common.domain.AddressValue;
import br.edu.ifba.agrofacil.common.domain.AreaValue;
import br.edu.ifba.agrofacil.common.domain.BrFormat;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.group.GroupRepository;
import br.edu.ifba.agrofacil.group.SpgGroup;
import br.edu.ifba.agrofacil.property.PlotRepository;
import br.edu.ifba.agrofacil.property.PlotView;
import br.edu.ifba.agrofacil.property.Property;
import br.edu.ifba.agrofacil.property.PropertyService;
import br.edu.ifba.agrofacil.property.WorkerRepository;
import br.edu.ifba.agrofacil.report.Labels;
import br.edu.ifba.agrofacil.spg.checklist.ChecklistService;
import br.edu.ifba.agrofacil.spg.visit.Visit;
import br.edu.ifba.agrofacil.spg.visit.VisitBrief;
import br.edu.ifba.agrofacil.spg.visit.VisitBriefBuilder;
import br.edu.ifba.agrofacil.spg.visit.VisitDtos;
import br.edu.ifba.agrofacil.spg.visit.VisitParticipant;
import br.edu.ifba.agrofacil.spg.visit.VisitRepository;
import br.edu.ifba.agrofacil.spg.visit.VisitService;
import br.edu.ifba.agrofacil.spg.visit.VisitStatus;
import br.edu.ifba.agrofacil.spg.visit.VisitStatusService;
import br.edu.ifba.agrofacil.spg.visit.VisitType;
import br.edu.ifba.agrofacil.user.AppUser;
import br.edu.ifba.agrofacil.user.FarmerProfile;
import br.edu.ifba.agrofacil.user.FarmerProfileRepository;
import br.edu.ifba.agrofacil.user.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os dados vão como mapas de textos já formatados: o template só exibe, e o mesmo mapa é gravado em
 * {@code data_snapshot}. A decisão de conformidade nunca entra nos dados (ADR-15).
 */
@Component
public class DocumentDataAssembler {

  public static final String NO_VERIFICATION_WARNING =
      "Não há visita de verificação registrada neste ciclo.";
  public static final String NO_LOCATION_WARNING = "Propriedade sem localização.";
  public static final String SMALL_GROUP_WARNING = "Grupo com menos de 3 agricultores.";

  /**
   * @param body dados do template
   */
  public record Assembled(
      String title, DocumentSubject subject, Map<String, Object> body, List<String> warnings) {}

  private final AssociationService associations;
  private final PropertyService properties;
  private final PlotRepository plots;
  private final WorkerRepository workers;
  private final UserRepository users;
  private final FarmerProfileRepository profiles;
  private final GroupRepository groups;
  private final MunicipalityNames municipalities;
  private final CertificationRepository certifications;
  private final CertificationReader certificationReader;
  private final VisitRepository visits;
  private final VisitService visitService;
  private final VisitStatusService visitStatus;
  private final VisitBriefBuilder briefs;
  private final ChecklistService checklist;
  private final MeasurementUnitRepository units;
  private final CropRepository crops;

  public DocumentDataAssembler(
      AssociationService associations,
      PropertyService properties,
      PlotRepository plots,
      WorkerRepository workers,
      UserRepository users,
      FarmerProfileRepository profiles,
      GroupRepository groups,
      MunicipalityNames municipalities,
      CertificationRepository certifications,
      CertificationReader certificationReader,
      VisitRepository visits,
      VisitService visitService,
      VisitStatusService visitStatus,
      VisitBriefBuilder briefs,
      ChecklistService checklist,
      MeasurementUnitRepository units,
      CropRepository crops) {
    this.associations = associations;
    this.properties = properties;
    this.plots = plots;
    this.workers = workers;
    this.users = users;
    this.profiles = profiles;
    this.groups = groups;
    this.municipalities = municipalities;
    this.certifications = certifications;
    this.certificationReader = certificationReader;
    this.visits = visits;
    this.visitService = visitService;
    this.visitStatus = visitStatus;
    this.briefs = briefs;
    this.checklist = checklist;
    this.units = units;
    this.crops = crops;
  }

  /**
   * @throws ApiException 422 DOCUMENT_PRECONDITION_FAILED quando falta algo que bloqueia (RN38)
   */
  @Transactional(readOnly = true)
  public Assembled assemble(CurrentUser user, GenerateDocumentRequest req) {
    return switch (req.kind()) {
      case MEMBERSHIP_REQUEST -> membership(user, req);
      case EVALUATION_REQUEST -> evaluation(user, req);
      case VISIT_PLAN -> visitPlan(user, req);
      case VISIT_FORM, PEER_VISIT_RECORD, VERIFICATION_REPORT -> visitDocument(user, req);
      case CONFORMITY_DECISION -> conformity(user, req);
      case CERTIFICATE -> certificate(user, req);
    };
  }

  // D1
  private Assembled membership(CurrentUser user, GenerateDocumentRequest req) {
    var property = properties.findVisible(user, require(req.propertyId(), "propertyId"));
    if (req.farmerId() != null && !req.farmerId().equals(property.getFarmerId())) {
      throw ApiException.validation(
          "farmerId", "MISMATCH", "A propriedade não é deste agricultor.");
    }
    var farmer = users.findById(property.getFarmerId()).orElseThrow();
    var profile = profiles.findById(farmer.getId()).orElse(null);
    var body = new LinkedHashMap<String, Object>();
    body.put("farmer", farmerData(farmer, profile));
    body.put("group", groupName(profile == null ? null : profile.getGroupId()));
    body.put("property", propertyData(property));
    body.put("plots", plotLines(property.getId()));
    var warnings = propertyWarnings(user, property);
    return new Assembled(
        DocumentKind.MEMBERSHIP_REQUEST.title() + " · " + farmer.getName(),
        new DocumentSubject(farmer.getId(), property.getId(), null, null, null, null, null),
        body,
        warnings);
  }

  // D2
  private Assembled evaluation(CurrentUser user, GenerateDocumentRequest req) {
    var group = findGroup(user, require(req.groupId(), "groupId"));
    if (req.propertyIds() == null || req.propertyIds().isEmpty()) {
      throw ApiException.validation("propertyIds", "REQUIRED", "Escolha ao menos uma propriedade.");
    }
    var rows = new ArrayList<Map<String, Object>>();
    for (var id : req.propertyIds()) {
      var p = properties.findVisible(user, id);
      var current = certificationReader.currentEntities(List.of(id)).get(id);
      rows.add(
          Map.of(
              "farmer", nameOf(p.getFarmerId()),
              "property", p.getName(),
              "municipality", municipalityOf(p),
              "area", areaText(p),
              "scope",
                  current != null && current.getScope() != null
                      ? current.getScope()
                      : nullToEmpty(p.getProductionDescription())));
    }
    var body = new LinkedHashMap<String, Object>();
    body.put("group", group.getName());
    body.put("properties", rows);
    return new Assembled(
        DocumentKind.EVALUATION_REQUEST.title() + " · " + group.getName(),
        new DocumentSubject(null, null, group.getId(), null, null, null, null),
        body,
        List.of());
  }

  // D3
  private Assembled visitPlan(CurrentUser user, GenerateDocumentRequest req) {
    var group = findGroup(user, require(req.groupId(), "groupId"));
    LocalDate from = require(req.from(), "from");
    LocalDate to = require(req.to(), "to");
    if (from.isAfter(to)) {
      throw ApiException.validation("from", "AFTER_TO", "A data inicial é depois da final.");
    }
    var list =
        visits
            .findByAssociationIdAndStatusAndPlannedOnBetweenAndDeletedAtIsNull(
                user.associationId(), VisitStatus.PLANNED, from, to)
            .stream()
            .filter(v -> group.getId().equals(v.getGroupId()))
            .sorted(Comparator.comparing(Visit::getPlannedOn))
            .toList();
    var rows = new ArrayList<Map<String, Object>>();
    for (var v : visitService.views(list, false)) {
      rows.add(
          Map.of(
              "date", BrFormat.date(v.plannedOn()),
              "type", typeLabel(v.type()),
              "property", v.property().name(),
              "farmer", nullToEmpty(v.property().farmerName()),
              "visitors", visitorNames(v, false)));
    }
    var body = new LinkedHashMap<String, Object>();
    body.put("group", group.getName());
    body.put("period", BrFormat.date(from) + " a " + BrFormat.date(to));
    body.put("visits", rows);
    return new Assembled(
        DocumentKind.VISIT_PLAN.title() + " · " + group.getName(),
        new DocumentSubject(null, null, group.getId(), null, null, from, to),
        body,
        List.of());
  }

  // D4, D5 e D6
  private Assembled visitDocument(CurrentUser user, GenerateDocumentRequest req) {
    var visit = visitService.findVisible(user, require(req.visitId(), "visitId"));
    var kind = req.kind();
    if (visit.getStatus() == VisitStatus.CANCELLED) {
      throw precondition("A visita foi cancelada.");
    }
    if (kind != DocumentKind.VISIT_FORM) {
      var expected =
          kind == DocumentKind.PEER_VISIT_RECORD ? VisitType.PEER : VisitType.VERIFICATION;
      if (visit.getStatus() != VisitStatus.DONE || visit.getType() != expected) {
        throw precondition(
            "Este documento precisa de uma visita %s concluída."
                .formatted(expected == VisitType.PEER ? "de pares" : "de verificação"));
      }
    }
    var view = visitService.views(List.of(visit), false).getFirst();
    var property = properties.findVisible(user, visit.getPropertyId());
    var brief = briefs.build(property.getId(), certificationReader.today());
    boolean filled = kind != DocumentKind.VISIT_FORM;
    Map<String, VisitDtos.Answer> answers =
        visitService.answers(visit).stream()
            .collect(
                Collectors.toMap(VisitDtos.Answer::itemCode, Function.identity(), (a, b) -> a));
    var questions = new ArrayList<Map<String, Object>>();
    for (var item : checklist.effective(user.associationId(), visit.getType().name(), false)) {
      var a = filled ? answers.get(item.getCode()) : null;
      questions.add(
          Map.of(
              "text",
              item.getFormalText(),
              "reference",
              nullToEmpty(item.getLegalReference()),
              "answer",
              a == null ? "" : a.answer().name(),
              "note",
              a == null || a.note() == null ? "" : a.note()));
    }
    var body = new LinkedHashMap<String, Object>();
    body.put("visitType", typeLabel(visit.getType()));
    body.put("plannedOn", BrFormat.date(visit.getPlannedOn()));
    body.put("unannounced", visit.isUnannounced() ? "sim" : "não");
    body.put("property", propertyData(property));
    body.put("farmer", nameOf(property.getFarmerId()));
    body.put("visitors", visitorNames(view, filled));
    body.put("brief", briefData(brief));
    body.put("questions", questions);
    body.put("filled", filled);
    if (filled) {
      var r = view.result();
      body.put("doneOn", BrFormat.date(r.doneOn()));
      body.put("issuesFound", r.issuesFound() ? "sim" : "não");
      body.put("improvementPoints", nullToEmpty(r.improvementPoints()));
      body.put("recommendations", nullToEmpty(r.recommendations()));
      body.put("notes", nullToEmpty(r.notes()));
      body.put("photos", view.attachments().size());
    }
    var signers = new ArrayList<String>();
    view.participants().stream()
        .filter(p -> !filled || Boolean.TRUE.equals(p.attended()))
        .forEach(p -> signers.add(p.person().name()));
    signers.add(nameOf(property.getFarmerId()) + " (visitado)");
    body.put("signers", signers);
    return new Assembled(
        kind.title() + " · " + property.getName() + " · " + BrFormat.date(visit.getPlannedOn()),
        new DocumentSubject(
            property.getFarmerId(),
            property.getId(),
            visit.getGroupId(),
            visit.getId(),
            null,
            null,
            null),
        body,
        propertyWarnings(user, property));
  }

  // D7
  private Assembled conformity(CurrentUser user, GenerateDocumentRequest req) {
    var property = properties.findVisible(user, require(req.propertyId(), "propertyId"));
    var status = visitStatus.statusOf(user.associationId(), property.getId());
    var profile = profiles.findById(property.getFarmerId()).orElse(null);
    UUID groupId = profile == null ? null : profile.getGroupId();
    var current =
        certificationReader.currentEntities(List.of(property.getId())).get(property.getId());
    var body = new LinkedHashMap<String, Object>();
    body.put("renewal", current != null);
    body.put("previousNumber", current == null ? "" : nullToEmpty(current.getNumber()));
    body.put("previousExpiresOn", current == null ? "" : BrFormat.date(current.getExpiresOn()));
    body.put("farmer", nameOf(property.getFarmerId()));
    body.put("group", groupName(groupId));
    body.put("property", propertyData(property));
    body.put("plots", plotLines(property.getId()));
    body.put("products", current == null ? List.of() : productNames(current));
    body.put("workers", workerNames(property.getId()));
    var cycleVisits =
        visits
            .findByPropertyIdAndStatusAndDeletedAtIsNullOrderByDoneOnDesc(
                property.getId(), VisitStatus.DONE)
            .stream()
            .filter(v ->
                !v.getDoneOn().isBefore(status.cycle().from())
                    && !v.getDoneOn().isAfter(status.cycle().to()))
            .sorted(Comparator.comparing(Visit::getDoneOn))
            .toList();
    var rows = new ArrayList<Map<String, Object>>();
    for (var v : visitService.views(cycleVisits, false)) {
      rows.add(
          Map.of(
              "date", BrFormat.date(v.result().doneOn()),
              "type", typeLabel(v.type()),
              "visitors", visitorNames(v, true),
              "issues",
                  v.result().issuesFound() ? "com pontos a melhorar" : "sem pontos a melhorar"));
    }
    body.put("visits", rows);
    // Art. 84, III: assinam todos os agricultores ativos do grupo na data da geração.
    var members = new ArrayList<String>();
    members.add(nameOf(property.getFarmerId()) + " (fornecedor)");
    if (groupId != null) {
      profiles.findFarmersOfGroup(groupId).stream()
          .filter(u ->
              u.getDeletedAt() == null && u.isActive() && !u.getId().equals(property.getFarmerId()))
          .map(AppUser::getName)
          .sorted()
          .forEach(members::add);
    }
    body.put("members", members);
    var warnings = new ArrayList<String>();
    if (status.verification().done() == 0) {
      warnings.add(NO_VERIFICATION_WARNING);
    }
    warnings.addAll(propertyWarnings(user, property));
    return new Assembled(
        DocumentKind.CONFORMITY_DECISION.title() + " · " + property.getName(),
        new DocumentSubject(
            property.getFarmerId(), property.getId(), groupId, null, null, null, null),
        body,
        warnings);
  }

  // D8
  private Assembled certificate(CurrentUser user, GenerateDocumentRequest req) {
    UUID id = require(req.certificationId(), "certificationId");
    var cert =
        certifications
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> precondition("A certificação não está cadastrada."));
    var property = properties.findVisible(user, cert.getPropertyId());
    var body = new LinkedHashMap<String, Object>();
    body.put("number", nullToEmpty(cert.getNumber()));
    body.put("farmer", nameOf(property.getFarmerId()));
    body.put("property", propertyData(property));
    body.put("products", productNames(cert));
    body.put("workers", workerNames(property.getId()));
    body.put("scope", nullToEmpty(cert.getScope()));
    body.put("issuedOn", BrFormat.date(cert.getIssuedOn()));
    body.put("expiresOn", BrFormat.date(cert.getExpiresOn()));
    return new Assembled(
        DocumentKind.CERTIFICATE.title() + " · " + property.getName(),
        new DocumentSubject(
            property.getFarmerId(), property.getId(), null, null, cert.getId(), null, null),
        body,
        List.of());
  }

  // Apoio

  private List<String> propertyWarnings(CurrentUser user, Property property) {
    var warnings = new ArrayList<String>();
    if (property.getLocation() == null || property.getLocation().getLatitude() == null) {
      warnings.add(NO_LOCATION_WARNING);
    }
    var st = visitStatus.statusOf(user.associationId(), property.getId());
    if (st != null && st.warnings().contains(VisitStatusService.SMALL_GROUP_WARNING)) {
      warnings.add(SMALL_GROUP_WARNING);
    }
    return warnings;
  }

  private Map<String, Object> farmerData(AppUser farmer, FarmerProfile profile) {
    var data = new LinkedHashMap<String, Object>();
    data.put("name", farmer.getName());
    data.put("cpf", formatCpf(farmer.getCpf()));
    data.put("caf", profile == null ? "" : nullToEmpty(profile.getCafNumber()));
    var address =
        profile == null ? null : AddressValue.toDto(profile.getAddress(), municipalities::nameOf);
    data.put(
        "address",
        address == null
            ? ""
            : String.join(
                ", ",
                Stream.of(
                        address.street(),
                        address.number(),
                        address.locality(),
                        address.municipalityName())
                    .filter(s -> s != null && !s.isBlank())
                    .toList()));
    return data;
  }

  private Map<String, Object> propertyData(Property p) {
    var data = new LinkedHashMap<String, Object>();
    data.put("name", p.getName());
    data.put("municipality", municipalityOf(p));
    data.put("area", areaText(p));
    data.put("accessDirections", nullToEmpty(p.getAccessDirections()));
    var loc = p.getLocation();
    data.put(
        "coordinates",
        loc == null || loc.getLatitude() == null
            ? ""
            : loc.getLatitude() + ", " + loc.getLongitude());
    return data;
  }

  private List<Map<String, Object>> plotLines(UUID propertyId) {
    return plots.findByPropertyIdAndDeletedAtIsNullOrderByName(propertyId).stream()
        .map(PlotView::of)
        .map(p ->
            Map.<String, Object>of(
                "name", p.name(),
                "status", Labels.plotStatus(p.status()),
                "area", Labels.area(p.area()),
                "conversionEndsOn", BrFormat.date(p.conversionEndsOn())))
        .toList();
  }

  private Map<String, Object> briefData(VisitBrief b) {
    Map<String, MeasurementUnit> unitNames =
        units.findAllByOrderBySortOrder().stream()
            .collect(Collectors.toMap(MeasurementUnit::getCode, Function.identity()));
    var data = new LinkedHashMap<String, Object>();
    data.put(
        "plantings",
        b.activePlantings().stream()
            .map(p ->
                p.crop() + " (" + p.plotName() + ", desde " + BrFormat.date(p.plantedOn()) + ")")
            .toList());
    data.put(
        "inputs",
        b.inputsLast12Months().stream()
            .map(i ->
                i.name()
                    + ": "
                    + i.timesUsed()
                    + " vez(es), última em "
                    + BrFormat.date(i.lastUsedOn())
                    + (i.needsAuthorization() ? " (precisa de autorização)" : ""))
            .toList());
    data.put(
        "harvests",
        b.harvestsLast12Months().stream()
            .map(h ->
                h.crop()
                    + ": "
                    + String.join(
                        " + ",
                        h.totals().stream().map(q -> Labels.quantity(q, unitNames)).toList()))
            .toList());
    data.put("incompleteRecords", b.incompleteRecords());
    data.put("lastRecordOn", BrFormat.date(b.lastRecordOn()));
    data.put(
        "previousRecommendations",
        b.previousVisit() == null ? "" : nullToEmpty(b.previousVisit().recommendations()));
    return data;
  }

  private List<String> productNames(Certification cert) {
    var cropIds =
        cert.getProducts().stream().map(p -> p.getCropId()).filter(Objects::nonNull).toList();
    Map<UUID, String> names =
        crops.findAllById(cropIds).stream().collect(Collectors.toMap(Crop::getId, Crop::getName));
    return cert.getProducts().stream()
        .map(
            p -> p.getCropId() != null ? names.getOrDefault(p.getCropId(), "") : p.getProductName())
        .sorted()
        .toList();
  }

  private List<String> workerNames(UUID propertyId) {
    return workers.findByPropertyIdAndDeletedAtIsNullOrderByName(propertyId).stream()
        .filter(w -> w.isActive())
        .map(w -> w.getName())
        .toList();
  }

  private String visitorNames(VisitDtos.VisitView v, boolean onlyAttended) {
    return String.join(
        ", ",
        v.participants().stream()
            .filter(p -> p.role() == VisitParticipant.Role.VISITOR)
            .filter(p -> !onlyAttended || Boolean.TRUE.equals(p.attended()))
            .map(p -> p.person().name())
            .toList());
  }

  private SpgGroup findGroup(CurrentUser user, UUID id) {
    return groups
        .findByIdAndAssociationIdAndDeletedAtIsNull(id, user.associationId())
        .orElseThrow(
            () -> ApiException.validation("groupId", "NOT_FOUND", "Grupo não encontrado."));
  }

  private String groupName(UUID groupId) {
    return groupId == null ? "" : groups.findById(groupId).map(SpgGroup::getName).orElse("");
  }

  private String nameOf(UUID userId) {
    return users.findById(userId).map(AppUser::getName).orElse("");
  }

  private String municipalityOf(Property p) {
    var id = p.getAddress() == null ? null : p.getAddress().getMunicipalityId();
    return id == null ? "" : municipalities.nameOf(id) + "/BA";
  }

  private static String areaText(Property p) {
    return Labels.area(AreaValue.toDto(p.getTotalArea()));
  }

  private static String typeLabel(VisitType type) {
    return type == VisitType.PEER ? "Visita de pares" : "Visita de verificação";
  }

  /** "12345678901" → "123.456.789-01". */
  private static String formatCpf(String cpf) {
    if (cpf == null || cpf.length() != 11) {
      return nullToEmpty(cpf);
    }
    return cpf.substring(0, 3)
        + "."
        + cpf.substring(3, 6)
        + "."
        + cpf.substring(6, 9)
        + "-"
        + cpf.substring(9);
  }

  private static <T> T require(T value, String field) {
    if (value == null) {
      throw ApiException.validation(field, "REQUIRED", "Este campo precisa ser preenchido.");
    }
    return value;
  }

  private static ApiException precondition(String message) {
    return new ApiException(ErrorCode.DOCUMENT_PRECONDITION_FAILED, message);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
