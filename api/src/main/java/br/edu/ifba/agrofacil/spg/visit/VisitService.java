// Agenda e resultado das visitas do SPG pelo painel e pelo app, com as regras RN31–RN36.
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.association.Association;
import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.attachment.Attachment;
import br.edu.ifba.agrofacil.attachment.AttachmentRepository;
import br.edu.ifba.agrofacil.attachment.AttachmentView;
import br.edu.ifba.agrofacil.catalog.MunicipalityNames;
import br.edu.ifba.agrofacil.certification.CertificationReader;
import br.edu.ifba.agrofacil.common.domain.BrFormat;
import br.edu.ifba.agrofacil.common.domain.GeoPointValue;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.error.FieldErrorItem;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import br.edu.ifba.agrofacil.common.web.VersionCheck;
import br.edu.ifba.agrofacil.property.Property;
import br.edu.ifba.agrofacil.property.PropertyRepository;
import br.edu.ifba.agrofacil.property.PropertyService;
import br.edu.ifba.agrofacil.spg.checklist.ChecklistService;
import br.edu.ifba.agrofacil.spg.checklist.VisitChecklistItem;
import br.edu.ifba.agrofacil.spg.people.PersonDirectory;
import br.edu.ifba.agrofacil.spg.people.PersonRef;
import br.edu.ifba.agrofacil.spg.visit.VisitDtos.ParticipantView;
import br.edu.ifba.agrofacil.spg.visit.VisitDtos.ParticipantWrite;
import br.edu.ifba.agrofacil.spg.visit.VisitDtos.ResultWrite;
import br.edu.ifba.agrofacil.spg.visit.VisitDtos.VisitView;
import br.edu.ifba.agrofacil.spg.visit.VisitDtos.VisitWrite;
import br.edu.ifba.agrofacil.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** O mesmo {@link #recordResult} atende o PUT do painel e a mutação do app (ADR-16). */
@Service
public class VisitService {

  /** RN35: o responsável corrige pelo app até 7 dias depois da visita. */
  static final int APP_EDIT_DAYS = 7;

  /** RN36: o visitador guarda a visita até 30 dias depois de concluída. */
  static final int PARTICIPANT_KEEP_DAYS = 30;

  private static final TypeReference<List<VisitDtos.Answer>> ANSWERS = new TypeReference<>() {};

  private final VisitRepository visits;
  private final VisitParticipantRepository participants;
  private final VisitorDirectory directory;
  private final VisitBriefBuilder briefs;
  private final VisitNotifier notifier;
  private final ChecklistService checklist;
  private final PersonDirectory people;
  private final PropertyService propertyService;
  private final PropertyRepository properties;
  private final UserRepository users;
  private final MunicipalityNames municipalities;
  private final AttachmentRepository attachments;
  private final AssociationService associations;
  private final CertificationReader certifications;
  private final JsonPatch jsonPatch;
  private final ObjectMapper mapper;
  private final Clock clock;

  public VisitService(
      VisitRepository visits,
      VisitParticipantRepository participants,
      VisitorDirectory directory,
      VisitBriefBuilder briefs,
      VisitNotifier notifier,
      ChecklistService checklist,
      PersonDirectory people,
      PropertyService propertyService,
      PropertyRepository properties,
      UserRepository users,
      MunicipalityNames municipalities,
      AttachmentRepository attachments,
      AssociationService associations,
      CertificationReader certifications,
      JsonPatch jsonPatch,
      ObjectMapper mapper,
      Clock clock) {
    this.visits = visits;
    this.participants = participants;
    this.directory = directory;
    this.briefs = briefs;
    this.notifier = notifier;
    this.checklist = checklist;
    this.people = people;
    this.propertyService = propertyService;
    this.properties = properties;
    this.users = users;
    this.municipalities = municipalities;
    this.attachments = attachments;
    this.associations = associations;
    this.certifications = certifications;
    this.jsonPatch = jsonPatch;
    this.mapper = mapper;
    this.clock = clock;
  }

  // Painel: agenda

  /**
   * Marca uma visita e avisa os envolvidos (RN30, RN31).
   *
   * @throws ApiException 422 VISITOR_NOT_ELIGIBLE (RN32) ou UNANNOUNCED_ONLY_VERIFICATION (RN31)
   */
  @Transactional
  public VisitView create(CurrentUser user, VisitWrite write) {
    var property = propertyService.findVisible(user, write.propertyId());
    var target = directory.target(property.getId());
    var current = certifications.currentEntities(List.of(property.getId())).get(property.getId());
    var visit =
        new Visit(
            user.associationId(),
            property.getId(),
            target.groupId(),
            current == null ? null : current.getId(),
            write.type(),
            user.id());
    visit.schedule(write.plannedOn(), write.unannounced(), user.id());
    var plan = planParticipants(user, visit, target, write);
    visits.saveAndFlush(visit);
    var saved = new ArrayList<VisitParticipant>();
    for (var p : plan.participants()) {
      var entity = new VisitParticipant(p.id(), visit.getId(), p.person());
      entity.update(p.role(), p.suggested(), p.reasons());
      saved.add(participants.save(entity));
    }
    visit.setRecorderParticipantId(plan.recorderId());
    refreshBrief(visit);
    visits.saveAndFlush(visit);
    var ctx = context(visit, property, target.farmerName());
    notifier.scheduled(ctx, farmerIds(saved), visitorNames(saved, plan.names()));
    return view(visit, true, plan.warnings());
  }

  /** Muda data, participantes, responsável ou "sem aviso"; só em PLANNED (RN34). */
  @Transactional
  public VisitView patch(CurrentUser user, UUID id, JsonNode body) {
    var visit = findVisible(user, id);
    var before = activeParticipants(visit.getId());
    var write = jsonPatch.merge(toWrite(visit, before), body, VisitWrite.class);
    VersionCheck.ensure(body, write.version(), visit.getVersion());
    if (!write.propertyId().equals(visit.getPropertyId()) || write.type() != visit.getType()) {
      throw ApiException.validation(
          "type", "NOT_EDITABLE", "Propriedade e tipo não mudam. Cancele e marque outra visita.");
    }
    var oldDate = visit.getPlannedOn();
    visit.schedule(write.plannedOn(), write.unannounced(), user.id());
    var property = properties.findById(visit.getPropertyId()).orElseThrow();
    var target = directory.target(property.getId());
    var plan = planParticipants(user, visit, target, write);

    Map<UUID, VisitParticipant> byPerson =
        before.stream().collect(Collectors.toMap(VisitParticipant::personId, p -> p));
    var keptFarmers = new HashSet<UUID>();
    var addedFarmers = new HashSet<UUID>();
    var finalList = new ArrayList<VisitParticipant>();
    for (var p : plan.participants()) {
      var existing = byPerson.remove(p.person().id());
      var entity =
          existing != null ? existing : new VisitParticipant(p.id(), visit.getId(), p.person());
      entity.update(p.role(), p.suggested(), p.reasons());
      finalList.add(participants.save(entity));
      if (p.person().isFarmer()) {
        (existing != null ? keptFarmers : addedFarmers).add(p.person().id());
      }
    }
    var removedFarmers = new HashSet<UUID>();
    for (var gone : byPerson.values()) {
      gone.markDeleted(clock.instant());
      if (gone.isFarmer()) {
        removedFarmers.add(gone.getFarmerId());
      }
    }
    visit.setRecorderParticipantId(plan.recorderId());
    visit.touch(); // participantes mudaram: o pull precisa levar a visita de novo
    refreshBrief(visit);
    visits.saveAndFlush(visit);
    notifier.changed(
        context(visit, property, target.farmerName()),
        removedFarmers,
        addedFarmers,
        keptFarmers,
        !oldDate.equals(visit.getPlannedOn()),
        visitorNames(finalList, plan.names()));
    return view(visit, true, plan.warnings());
  }

  /** RN34: cancela com motivo; só em PLANNED. */
  @Transactional
  public void cancel(CurrentUser user, UUID id, String reason) {
    var visit = findVisible(user, id);
    visit.cancel(reason.trim(), user.id());
    visits.saveAndFlush(visit);
    var property = properties.findById(visit.getPropertyId()).orElseThrow();
    var owner = users.findById(property.getFarmerId()).orElseThrow();
    notifier.cancelled(
        context(visit, property, owner.getName()), farmerIds(activeParticipants(visit.getId())));
  }

  /** Exclusão lógica, para lançamento errado (inclusive de visita concluída, RN34). */
  @Transactional
  public void delete(CurrentUser user, UUID id) {
    findVisible(user, id).markDeleted(clock.instant());
  }

  // Resultado

  /** Painel: digita a folha de papel ou corrige um resultado, a qualquer momento (RN35). */
  @Transactional
  public VisitView putResult(CurrentUser user, UUID id, ResultWrite write) {
    var visit = findVisible(user, id);
    VersionCheck.ensure(write.version(), visit.getVersion());
    recordResult(visit, write, Visit.ResultSource.WEB, user.id());
    return view(visit, true, List.of());
  }

  /**
   * App: só o responsável pelo registro, até 7 dias depois da visita (RN35). Outro usuário recebe
   * NOT_FOUND, para não revelar a visita.
   *
   * @return a nova versão da visita
   */
  @Transactional
  public Long recordFromApp(CurrentUser farmer, UUID id, ResultWrite write) {
    var visit =
        visits
            .findById(id)
            .filter(v -> !v.isDeleted() && v.getStatus() != VisitStatus.CANCELLED)
            .orElseThrow(ApiException::notFound);
    var recorder =
        activeParticipants(visit.getId()).stream()
            .filter(p -> p.getId().equals(visit.getRecorderParticipantId()))
            .filter(p -> farmer.id().equals(p.getFarmerId()))
            .findFirst()
            .orElseThrow(ApiException::notFound);
    LocalDate today = certifications.today();
    LocalDate limitFor = visit.getDoneOn() != null ? visit.getDoneOn() : write.doneOn();
    if (today.isAfter(limitFor.plusDays(APP_EDIT_DAYS))
        || today.isAfter(write.doneOn().plusDays(APP_EDIT_DAYS))) {
      throw new ApiException(ErrorCode.VISIT_NOT_EDITABLE);
    }
    recordResult(visit, write, Visit.ResultSource.MOBILE, farmer.id());
    var property = properties.findById(visit.getPropertyId()).orElseThrow();
    var owner = users.findById(property.getFarmerId()).orElseThrow();
    notifier.resultFromApp(
        context(visit, property, owner.getName()),
        users.findById(recorder.getFarmerId()).orElseThrow().getName());
    return visit.getVersion();
  }

  /** Valida e grava o resultado; a visita passa a DONE (RN35). */
  private void recordResult(Visit visit, ResultWrite write, Visit.ResultSource source, UUID by) {
    var errors = new ArrayList<FieldErrorItem>();
    LocalDate today = certifications.today();
    if (write.doneOn().isAfter(today)) {
      errors.add(new FieldErrorItem("doneOn", "FUTURE", "A data não pode ser depois de hoje."));
    }
    var current = activeParticipants(visit.getId());
    var byId = current.stream().collect(Collectors.toMap(VisitParticipant::getId, p -> p));
    var attended = new HashSet<>(write.attendedParticipantIds());
    for (var pid : attended) {
      if (!byId.containsKey(pid)) {
        errors.add(
            new FieldErrorItem(
                "attendedParticipantIds", "NOT_FOUND", "Participante não encontrado na visita."));
      }
    }
    boolean visitorAttended =
        attended.stream()
            .map(byId::get)
            .anyMatch(p -> p != null && p.getRole() == VisitParticipant.Role.VISITOR);
    if (!visitorAttended) {
      errors.add(
          new FieldErrorItem(
              "attendedParticipantIds",
              "VISITOR_REQUIRED",
              "Marque pelo menos 1 visitador presente."));
    }
    if (Boolean.TRUE.equals(write.issuesFound())
        && (write.improvementPoints() == null || write.improvementPoints().isBlank())) {
      errors.add(
          new FieldErrorItem("improvementPoints", "REQUIRED", "Conte o que precisa melhorar."));
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    checkAnswers(visit, write.answers());
    for (var p : current) {
      p.setAttended(attended.contains(p.getId()));
    }
    visit.recordResult(
        write.doneOn(),
        mapper.writeValueAsString(write.answers()),
        write.issuesFound(),
        blankToNull(write.improvementPoints()),
        blankToNull(write.recommendations()),
        blankToNull(write.notes()),
        GeoPointValue.from(write.location()),
        source,
        by,
        clock.instant());
    visits.saveAndFlush(visit);
  }

  /** Uma resposta para cada pergunta ativa do roteiro do tipo (RN35). */
  private void checkAnswers(Visit visit, List<VisitDtos.Answer> answers) {
    var items =
        checklist.effective(visit.getAssociationId(), visit.getType().name(), false).stream()
            .map(VisitChecklistItem::getCode)
            .toList();
    var given = answers.stream().map(VisitDtos.Answer::itemCode).collect(Collectors.toSet());
    var unknown = given.stream().filter(code -> !items.contains(code)).sorted().toList();
    if (!unknown.isEmpty()) {
      throw ApiException.validation(
          "answers", "UNKNOWN_ITEM", "Pergunta fora do roteiro: " + String.join(", ", unknown));
    }
    var missing = items.stream().filter(code -> !given.contains(code)).toList();
    if (!missing.isEmpty()) {
      throw ApiException.withErrors(
          ErrorCode.CHECKLIST_INCOMPLETE,
          ErrorCode.CHECKLIST_INCOMPLETE.defaultMessage(),
          missing.stream().map(code -> new FieldErrorItem("answers", "MISSING", code)).toList());
    }
  }

  // Leitura

  @Transactional(readOnly = true)
  public PageResponse<VisitView> list(CurrentUser user, VisitFilter filter, Pageable page) {
    Page<Visit> result = visits.findAll(specFor(user.associationId(), filter), page);
    return PageResponse.of(result, views(result.getContent(), false));
  }

  @Transactional(readOnly = true)
  public VisitView get(CurrentUser user, UUID id) {
    return view(findVisible(user, id), true, List.of());
  }

  /** Visita da associação, não excluída; 404 caso contrário. */
  @Transactional(readOnly = true)
  public Visit findVisible(CurrentUser user, UUID id) {
    return visits
        .findByIdAndAssociationIdAndDeletedAtIsNull(id, user.associationId())
        .orElseThrow(ApiException::notFound);
  }

  /**
   * Pull do agricultor: separa o que ele pode ver (com ou sem resumo) do que vira lápide (RN31,
   * RN36, doc 1 §4.8).
   *
   * @param tombstones recebe os ids das visitas que devem sumir do celular
   */
  @Transactional(readOnly = true)
  public List<VisitView> viewsForFarmer(
      UUID farmerId, List<Visit> changed, Collection<UUID> tombstones) {
    if (changed.isEmpty()) {
      return List.of();
    }
    LocalDate today = certifications.today();
    var owners =
        properties
            .findAllById(changed.stream().map(Visit::getPropertyId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Property::getId, Property::getFarmerId));
    var activeByVisit = activeParticipantsOf(changed);
    var withBrief = new ArrayList<Visit>();
    var withoutBrief = new ArrayList<Visit>();
    for (var v : changed) {
      boolean participant =
          activeByVisit.getOrDefault(v.getId(), List.of()).stream()
              .anyMatch(p -> farmerId.equals(p.getFarmerId()));
      boolean ownerSees =
          farmerId.equals(owners.get(v.getPropertyId()))
              && (!v.isUnannounced() || v.getStatus() == VisitStatus.DONE);
      boolean participantSees =
          participant
              && (v.isPlanned()
                  || (v.getStatus() == VisitStatus.DONE
                      && !v.getDoneOn().isBefore(today.minusDays(PARTICIPANT_KEEP_DAYS))));
      if (v.isDeleted() || (!participantSees && !ownerSees)) {
        tombstones.add(v.getId());
      } else if (participantSees) {
        withBrief.add(v);
      } else {
        withoutBrief.add(v);
      }
    }
    var result = new ArrayList<>(views(withBrief, true));
    result.addAll(views(withoutBrief, false));
    return result;
  }

  /** Recalcula o resumo das visitas marcadas para os próximos dias (rotina diária, RN36). */
  @Transactional
  public int refreshUpcomingBriefs(LocalDate today, int days) {
    int changed = 0;
    for (var v :
        visits.findByStatusAndPlannedOnBetweenAndDeletedAtIsNull(
            VisitStatus.PLANNED, today, today.plusDays(days))) {
      if (refreshBrief(v)) {
        changed++;
      }
    }
    return changed;
  }

  // Apoio

  /** Participante já validado, pronto para gravar. */
  private record Planned(
      UUID id,
      PersonRef person,
      VisitParticipant.Role role,
      boolean suggested,
      List<String> reasons) {}

  private record Plan(
      List<Planned> participants,
      UUID recorderId,
      List<String> warnings,
      Map<UUID, String> names) {}

  /** Aplica a RN32 a todos os participantes e escolhe o responsável pelo registro. */
  private Plan planParticipants(
      CurrentUser user, Visit visit, VisitorModel.Target target, VisitWrite write) {
    Association association = associations.get(user.associationId());
    var resolved = new ArrayList<PersonRef>();
    var seen = new HashSet<UUID>();
    for (int i = 0; i < write.participants().size(); i++) {
      var ref =
          people.resolve(
              user.associationId(),
              write.participants().get(i).person(),
              "participants[%d].person".formatted(i));
      if (!seen.add(ref.id())) {
        throw ApiException.validation(
            "participants[%d]".formatted(i), "DUPLICATE", ref.name() + " aparece duas vezes.");
      }
      resolved.add(ref);
    }
    var candidates =
        directory.candidates(
            user.associationId(),
            target,
            write.plannedOn(),
            certifications.today(),
            resolved,
            visit.getId());
    var errors = new ArrayList<FieldErrorItem>();
    var warnings = new ArrayList<String>();
    var planned = new ArrayList<Planned>();
    var names = new HashMap<UUID, String>();
    boolean hasVisitor = false;
    for (int i = 0; i < resolved.size(); i++) {
      var ref = resolved.get(i);
      ParticipantWrite pw = write.participants().get(i);
      String field = "participants[%d]".formatted(i);
      names.put(ref.id(), ref.name());
      var c = candidates.get(ref.id());
      VisitorEligibility.check(
              c, target, visit.getType(), pw.role(), association.isVerificationSameGroupAllowed())
          .ifPresent(reason ->
              errors.add(new FieldErrorItem(field, "NOT_ELIGIBLE", ref.name() + ": " + reason)));
      if (c.otherVisitSameDay()) {
        warnings.add(
            "%s já tem outra visita marcada em %s."
                .formatted(ref.name(), BrFormat.date(write.plannedOn())));
      }
      hasVisitor |= pw.role() == VisitParticipant.Role.VISITOR;
      planned.add(
          new Planned(
              pw.id() != null ? pw.id() : UUID.randomUUID(),
              ref,
              pw.role(),
              Boolean.TRUE.equals(pw.suggested()),
              pw.suggestionReasons() == null ? List.of() : pw.suggestionReasons()));
    }
    if (!hasVisitor) {
      errors.add(
          new FieldErrorItem(
              "participants", "VISITOR_REQUIRED", "A visita precisa de pelo menos 1 visitador."));
    }
    UUID recorder = null;
    if (write.recorderId() != null) {
      recorder =
          planned.stream()
              .filter(p ->
                  p.id().equals(write.recorderId()) || p.person().id().equals(write.recorderId()))
              .filter(p -> p.person().isFarmer() && p.role() == VisitParticipant.Role.VISITOR)
              .map(Planned::id)
              .findFirst()
              .orElse(null);
      if (recorder == null) {
        errors.add(
            new FieldErrorItem(
                "recorderId",
                "NOT_ELIGIBLE",
                "O responsável pelo registro precisa ser um agricultor visitador da visita."));
      }
    }
    if (!errors.isEmpty()) {
      throw ApiException.withErrors(
          ErrorCode.VISITOR_NOT_ELIGIBLE, ErrorCode.VISITOR_NOT_ELIGIBLE.defaultMessage(), errors);
    }
    // Os participantes antigos mantêm o id: o responsável escolhido pelo id antigo continua
    // valendo.
    var existingIds =
        activeParticipants(visit.getId()).stream()
            .collect(Collectors.toMap(VisitParticipant::personId, VisitParticipant::getId));
    var finalPlanned = new ArrayList<Planned>();
    UUID finalRecorder = recorder;
    for (var p : planned) {
      UUID keep = existingIds.get(p.person().id());
      if (keep != null && !keep.equals(p.id())) {
        if (p.id().equals(recorder)) {
          finalRecorder = keep;
        }
        finalPlanned.add(new Planned(keep, p.person(), p.role(), p.suggested(), p.reasons()));
      } else {
        finalPlanned.add(p);
      }
    }
    return new Plan(finalPlanned, finalRecorder, warnings, names);
  }

  /** Grava o resumo se mudou (RN36). */
  private boolean refreshBrief(Visit visit) {
    var brief = briefs.build(visit.getPropertyId(), certifications.today());
    return visit.refreshBrief(mapper.writeValueAsString(brief), clock.instant());
  }

  private VisitWrite toWrite(Visit visit, List<VisitParticipant> current) {
    var names =
        people.names(
            current.stream().map(VisitParticipant::getFarmerId).filter(Objects::nonNull).toList(),
            current.stream()
                .map(VisitParticipant::getCollaboratorId)
                .filter(Objects::nonNull)
                .toList());
    return new VisitWrite(
        visit.getPropertyId(),
        visit.getType(),
        visit.isUnannounced(),
        visit.getPlannedOn(),
        current.stream()
            .map(p ->
                new ParticipantWrite(
                    p.getId(),
                    personOf(p, names),
                    p.getRole(),
                    p.isSuggested(),
                    p.getSuggestionReasons()))
            .toList(),
        visit.getRecorderParticipantId(),
        visit.getVersion());
  }

  private List<VisitParticipant> activeParticipants(UUID visitId) {
    return participants.findByVisitIdAndDeletedAtIsNull(visitId);
  }

  private Map<UUID, List<VisitParticipant>> activeParticipantsOf(List<Visit> list) {
    return participants.findByVisitIdIn(list.stream().map(Visit::getId).toList()).stream()
        .filter(p -> !p.isDeleted())
        .collect(Collectors.groupingBy(VisitParticipant::getVisitId));
  }

  private VisitNotifier.Context context(Visit visit, Property property, String visitedName) {
    return new VisitNotifier.Context(
        visit, property.getName(), property.getFarmerId(), visitedName);
  }

  private static Set<UUID> farmerIds(Collection<VisitParticipant> list) {
    return list.stream()
        .map(VisitParticipant::getFarmerId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private static List<String> visitorNames(List<VisitParticipant> list, Map<UUID, String> names) {
    return list.stream()
        .filter(p -> p.getRole() == VisitParticipant.Role.VISITOR)
        .map(p -> names.get(p.personId()))
        .filter(Objects::nonNull)
        .toList();
  }

  private VisitView view(Visit visit, boolean includeBrief, List<String> warnings) {
    var v = views(List.of(visit), includeBrief).getFirst();
    return new VisitView(
        v.id(),
        v.propertyId(),
        v.property(),
        v.groupId(),
        v.certificationId(),
        v.type(),
        v.unannounced(),
        v.status(),
        v.plannedOn(),
        v.participants(),
        v.recorderId(),
        v.result(),
        v.cancelReason(),
        v.brief(),
        v.attachments(),
        warnings,
        v.createdAt(),
        v.updatedAt(),
        v.version());
  }

  /** Converte em lote: participantes, nomes, propriedades e anexos sem consulta por linha. */
  public List<VisitView> views(List<Visit> list, boolean includeBrief) {
    if (list.isEmpty()) {
      return List.of();
    }
    var byVisit = activeParticipantsOf(list);
    var all = byVisit.values().stream().flatMap(List::stream).toList();
    var names =
        people.names(
            all.stream().map(VisitParticipant::getFarmerId).filter(Objects::nonNull).toList(),
            all.stream()
                .map(VisitParticipant::getCollaboratorId)
                .filter(Objects::nonNull)
                .toList());
    var propertyById =
        properties.findAllById(list.stream().map(Visit::getPropertyId).distinct().toList()).stream()
            .collect(Collectors.toMap(Property::getId, p -> p));
    var ownerNames =
        people.names(propertyById.values().stream().map(Property::getFarmerId).toList(), List.of());
    var files =
        attachments
            .findByOwnerTypeAndOwnerIdInAndDeletedAtIsNull(
                Attachment.OwnerType.VISIT, list.stream().map(Visit::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(Attachment::getOwnerId));
    var result = new ArrayList<VisitView>();
    for (var v : list) {
      var p = propertyById.get(v.getPropertyId());
      var ps = byVisit.getOrDefault(v.getId(), List.of());
      result.add(
          new VisitView(
              v.getId(),
              v.getPropertyId(),
              new VisitDtos.PropertyRef(
                  p.getId(),
                  p.getName(),
                  p.getFarmerId(),
                  ownerNames.get(p.getFarmerId()),
                  p.getAddress() == null || p.getAddress().getMunicipalityId() == null
                      ? null
                      : municipalities.nameOf(p.getAddress().getMunicipalityId())),
              v.getGroupId(),
              v.getCertificationId(),
              v.getType(),
              v.isUnannounced(),
              v.getStatus(),
              v.getPlannedOn(),
              ps.stream()
                  .map(x ->
                      new ParticipantView(
                          x.getId(),
                          personOf(x, names),
                          x.getRole(),
                          x.isSuggested(),
                          x.getSuggestionReasons(),
                          x.getAttended()))
                  .toList(),
              v.getRecorderParticipantId(),
              result(v, ps),
              v.getCancelReason(),
              includeBrief ? brief(v) : null,
              files.getOrDefault(v.getId(), List.of()).stream().map(AttachmentView::of).toList(),
              List.of(),
              v.getCreatedAt(),
              v.getUpdatedAt(),
              v.getVersion()));
    }
    return result;
  }

  private VisitDtos.Result result(Visit v, List<VisitParticipant> ps) {
    if (v.getStatus() != VisitStatus.DONE) {
      return null;
    }
    return new VisitDtos.Result(
        v.getDoneOn(),
        ps.stream()
            .filter(p -> Boolean.TRUE.equals(p.getAttended()))
            .map(VisitParticipant::getId)
            .toList(),
        answers(v),
        Boolean.TRUE.equals(v.getIssuesFound()),
        v.getImprovementPoints(),
        v.getRecommendations(),
        v.getNotes(),
        GeoPointValue.toDto(v.getLocation(), null),
        v.getResultSource(),
        v.getResultRecordedBy(),
        v.getResultRecordedAt());
  }

  /** Respostas gravadas no JSON da visita. */
  public List<VisitDtos.Answer> answers(Visit v) {
    return v.getAnswers() == null ? List.of() : mapper.readValue(v.getAnswers(), ANSWERS);
  }

  private VisitBrief brief(Visit v) {
    if (v.getBrief() == null) {
      return null;
    }
    return mapper.readValue(v.getBrief(), VisitBrief.class).withGeneratedAt(v.getUpdatedAt());
  }

  private static PersonRef personOf(VisitParticipant p, Map<UUID, String> names) {
    return p.isFarmer()
        ? PersonRef.farmer(p.getFarmerId(), names.get(p.getFarmerId()))
        : PersonRef.collaborator(p.getCollaboratorId(), names.get(p.getCollaboratorId()));
  }

  private static Specification<Visit> specFor(UUID associationId, VisitFilter f) {
    return (root, query, cb) -> {
      var predicates = new ArrayList<Predicate>();
      predicates.add(cb.equal(root.get("associationId"), associationId));
      predicates.add(cb.isNull(root.get("deletedAt")));
      if (f.propertyId() != null) {
        predicates.add(cb.equal(root.get("propertyId"), f.propertyId()));
      }
      if (f.groupId() != null) {
        predicates.add(cb.equal(root.get("groupId"), f.groupId()));
      }
      if (f.type() != null) {
        predicates.add(cb.equal(root.get("type"), f.type()));
      }
      if (f.status() != null) {
        predicates.add(cb.equal(root.get("status"), f.status()));
      }
      if (f.from() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("plannedOn"), f.from()));
      }
      if (f.to() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("plannedOn"), f.to()));
      }
      if (f.personId() != null) {
        var sub = query.subquery(UUID.class);
        var vp = sub.from(VisitParticipant.class);
        sub.select(vp.get("visitId"))
            .where(
                cb.isNull(vp.get("deletedAt")),
                cb.or(
                    cb.equal(vp.get("farmerId"), f.personId()),
                    cb.equal(vp.get("collaboratorId"), f.personId())));
        predicates.add(root.get("id").in(sub));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
