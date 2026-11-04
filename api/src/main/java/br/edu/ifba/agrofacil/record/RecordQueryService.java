// Consultas de registros do painel: lista filtrada, detalhe e histórico de alterações (doc 1
// §3.9.2).
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.attachment.Attachment;
import br.edu.ifba.agrofacil.attachment.AttachmentService;
import br.edu.ifba.agrofacil.catalog.Crop;
import br.edu.ifba.agrofacil.catalog.CropRepository;
import br.edu.ifba.agrofacil.catalog.FarmInput;
import br.edu.ifba.agrofacil.catalog.FarmInputRepository;
import br.edu.ifba.agrofacil.common.domain.Area;
import br.edu.ifba.agrofacil.common.domain.AreaValue;
import br.edu.ifba.agrofacil.common.domain.Quantity;
import br.edu.ifba.agrofacil.common.domain.QuantityValue;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.persistence.AuditRevision;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.IdName;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import br.edu.ifba.agrofacil.correction.CorrectionRequest;
import br.edu.ifba.agrofacil.correction.CorrectionRequestRepository;
import br.edu.ifba.agrofacil.correction.CorrectionRequestView;
import br.edu.ifba.agrofacil.property.Plot;
import br.edu.ifba.agrofacil.property.PlotRepository;
import br.edu.ifba.agrofacil.property.Property;
import br.edu.ifba.agrofacil.property.PropertyRepository;
import br.edu.ifba.agrofacil.record.RecordHistoryEntry.ChangeType;
import br.edu.ifba.agrofacil.user.AppUser;
import br.edu.ifba.agrofacil.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class RecordQueryService {

  /** Campos calculados ou de controle, que não entram no "o que mudou" do histórico. */
  private static final Set<String> HISTORY_IGNORED =
      Set.of(
          "id",
          "type",
          "number",
          "deviceId",
          "source",
          "createdById",
          "updatedById",
          "completeness",
          "missingFields",
          "openCorrectionRequests",
          "createdAt",
          "updatedAt",
          "version",
          "cycleStatus",
          "finishedOn");

  private final RecordRepository records;
  private final RecordService recordService;
  private final AttachmentService attachments;
  private final CorrectionRequestRepository corrections;
  private final CropRepository crops;
  private final FarmInputRepository inputs;
  private final PropertyRepository properties;
  private final PlotRepository plots;
  private final UserRepository users;
  private final EntityManager entityManager;
  private final ObjectMapper mapper;

  public RecordQueryService(
      RecordRepository records,
      RecordService recordService,
      AttachmentService attachments,
      CorrectionRequestRepository corrections,
      CropRepository crops,
      FarmInputRepository inputs,
      PropertyRepository properties,
      PlotRepository plots,
      UserRepository users,
      EntityManager entityManager,
      ObjectMapper mapper) {
    this.records = records;
    this.recordService = recordService;
    this.attachments = attachments;
    this.corrections = corrections;
    this.crops = crops;
    this.inputs = inputs;
    this.properties = properties;
    this.plots = plots;
    this.users = users;
    this.entityManager = entityManager;
    this.mapper = mapper;
  }

  /** Lista paginada; ordenação padrão {@code performedOn,desc}. */
  @Transactional(readOnly = true)
  public PageResponse<RecordListItem> list(CurrentUser user, RecordFilter filter, Pageable page) {
    var result = records.findAll(specFor(user.associationId(), filter), page);
    return PageResponse.of(result, listItems(user.associationId(), result.getContent()));
  }

  /**
   * Todos os registros do filtro, sem paginação, em ordem de data (relatórios e exportação). O
   * volume de uma associação é pequeno o bastante para caber na memória.
   */
  @Transactional(readOnly = true)
  public List<RecordListItem> listAll(CurrentUser user, RecordFilter filter) {
    var all =
        records.findAll(
            specFor(user.associationId(), filter),
            Sort.by("performedOn").ascending().and(Sort.by("recordNumber").ascending()));
    return listItems(user.associationId(), all);
  }

  @Transactional(readOnly = true)
  public RecordDetail detail(CurrentUser user, UUID id) {
    var record = recordService.findVisible(user, id);
    return new RecordDetail(
        recordService.views(List.of(record)).getFirst(),
        attachments.viewsFor(Attachment.OwnerType.RECORD, id),
        corrections.findByRecordIdOrderByCreatedAtDesc(id).stream()
            .map(CorrectionRequestView::of)
            .toList());
  }

  /**
   * Revisões do registro, da mais antiga para a mais nova, com os campos que mudaram em cada uma
   * (lidas do Envers).
   */
  @Transactional(readOnly = true)
  public List<RecordHistoryEntry> history(CurrentUser user, UUID id) {
    var record =
        records
            .findById(id)
            .filter(r -> r.getAssociationId().equals(user.associationId()))
            .orElseThrow(ApiException::notFound);
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        AuditReaderFactory.get(entityManager)
            .createQuery()
            .forRevisionsOfEntity(ManagementRecord.class, false, true)
            .add(AuditEntity.id().eq(record.getId()))
            .addOrder(AuditEntity.revisionNumber().asc())
            .getResultList();

    Map<UUID, AppUser> authors =
        users
            .findByIdIn(
                rows.stream()
                    .map(row -> ((AuditRevision) row[1]).getUserId())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(AppUser::getId, Function.identity()));

    var entries = new ArrayList<RecordHistoryEntry>();
    ObjectNode previous = null;
    boolean wasDeleted = false;
    for (Object[] row : rows) {
      var snapshot = (ManagementRecord) row[0];
      var revision = (AuditRevision) row[1];
      var revisionType = (RevisionType) row[2];
      ObjectNode current = comparable(snapshot);
      ChangeType changeType;
      if (previous == null || revisionType == RevisionType.ADD) {
        changeType = ChangeType.CREATED;
      } else if (!wasDeleted && snapshot.isDeleted()) {
        changeType = ChangeType.DELETED;
      } else {
        changeType = ChangeType.UPDATED;
      }
      var author = revision.getUserId() == null ? null : authors.get(revision.getUserId());
      entries.add(
          new RecordHistoryEntry(
              revision.getId(),
              Instant.ofEpochMilli(revision.getRevisionAt()),
              author == null
                  ? null
                  : new RecordHistoryEntry.Author(
                      author.getId(), author.getName(), author.getRole()),
              revision.getSource(),
              changeType,
              diff(previous, current)));
      previous = current;
      wasDeleted = snapshot.isDeleted();
    }
    return entries;
  }

  /** Converte uma página de registros em linhas, resolvendo nomes em lote (sem N+1). */
  private List<RecordListItem> listItems(UUID associationId, List<ManagementRecord> page) {
    if (page.isEmpty()) {
      return List.of();
    }
    Map<UUID, Crop> cropById =
        crops.findVisible(associationId).stream()
            .collect(Collectors.toMap(Crop::getId, Function.identity()));
    Map<UUID, FarmInput> inputById =
        inputs.findVisible(associationId).stream()
            .collect(Collectors.toMap(FarmInput::getId, Function.identity()));
    Map<UUID, ManagementRecord> plantings =
        records
            .findByIdIn(
                page.stream()
                    .map(r -> r instanceof Harvest h ? h.getPlantingId() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(ManagementRecord::getId, Function.identity()));
    Map<UUID, String> farmerNames =
        users.findByIdIn(ids(page, ManagementRecord::getFarmerId)).stream()
            .collect(Collectors.toMap(AppUser::getId, AppUser::getName));
    Map<UUID, String> propertyNames =
        properties.findAllById(ids(page, ManagementRecord::getPropertyId)).stream()
            .collect(Collectors.toMap(Property::getId, Property::getName));
    Map<UUID, String> plotNames =
        plots.findAllById(ids(page, ManagementRecord::getPlotId)).stream()
            .collect(Collectors.toMap(Plot::getId, Plot::getName));
    var open = recordService.openCorrections(page.stream().map(ManagementRecord::getId).toList());

    return page.stream()
        .map(r ->
            new RecordListItem(
                r.getId(),
                r.type(),
                r.getRecordNumber(),
                r.getPerformedOn(),
                new IdName(r.getFarmerId(), farmerNames.get(r.getFarmerId())),
                new IdName(r.getPropertyId(), propertyNames.get(r.getPropertyId())),
                r.getPlotId() == null
                    ? null
                    : new IdName(r.getPlotId(), plotNames.get(r.getPlotId())),
                subject(r, cropById, inputById, plantings),
                quantity(r),
                area(r),
                r.getCompleteness(),
                open.getOrDefault(r.getId(), 0L),
                r.getSource(),
                r.getUpdatedAt()))
        .toList();
  }

  /** Nome da cultura, do insumo ou do produto; o texto de "Outro" quando não há catálogo. */
  private static String subject(
      ManagementRecord r,
      Map<UUID, Crop> crops,
      Map<UUID, FarmInput> inputs,
      Map<UUID, ManagementRecord> plantings) {
    return switch (r) {
      case Planting p -> cropName(p.getCropId(), p.getCropOtherName(), crops);
      case InputApplication a -> inputName(a.getInputId(), a.getInputOtherName(), inputs);
      case Harvest h ->
          plantings.get(h.getPlantingId()) instanceof Planting p
              ? cropName(p.getCropId(), p.getCropOtherName(), crops)
              : null;
      case InputPurchase ip -> inputName(ip.getInputId(), ip.getInputOtherName(), inputs);
      case Outflow o -> cropName(o.getCropId(), o.getProductOtherName(), crops);
      default -> null;
    };
  }

  private static String cropName(UUID id, String other, Map<UUID, Crop> crops) {
    return id != null && crops.containsKey(id) ? crops.get(id).getName() : other;
  }

  private static String inputName(UUID id, String other, Map<UUID, FarmInput> inputs) {
    return id != null && inputs.containsKey(id) ? inputs.get(id).getName() : other;
  }

  private static Quantity quantity(ManagementRecord r) {
    return switch (r) {
      case Planting p -> QuantityValue.toDto(p.getPropagationQuantity());
      case InputApplication a -> QuantityValue.toDto(a.getQuantity());
      case Harvest h -> QuantityValue.toDto(h.getQuantity());
      case InputPurchase ip -> QuantityValue.toDto(ip.getQuantity());
      case Outflow o -> QuantityValue.toDto(o.getQuantity());
      default -> null;
    };
  }

  private static Area area(ManagementRecord r) {
    return switch (r) {
      case Planting p -> AreaValue.toDto(p.getPlantedArea());
      case InputApplication a -> AreaValue.toDto(a.getAppliedArea());
      case Harvest h -> AreaValue.toDto(h.getHarvestedArea());
      default -> null;
    };
  }

  private static Set<UUID> ids(
      Collection<ManagementRecord> list, Function<ManagementRecord, UUID> getter) {
    return list.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  /** Estado do registro no formato JSON do contrato, sem os campos de controle. */
  private ObjectNode comparable(ManagementRecord snapshot) {
    ObjectNode node = mapper.valueToTree(RecordMapper.toView(snapshot, 0));
    HISTORY_IGNORED.forEach(node::remove);
    return node;
  }

  private static List<RecordHistoryEntry.Change> diff(ObjectNode before, ObjectNode after) {
    var fields = new LinkedHashSet<String>();
    if (before != null) {
      fields.addAll(before.propertyNames());
    }
    fields.addAll(after.propertyNames());
    var changes = new ArrayList<RecordHistoryEntry.Change>();
    for (String field : fields) {
      JsonNode b = before == null ? null : before.get(field);
      JsonNode a = after.get(field);
      boolean bothEmpty = isEmpty(b) && isEmpty(a);
      if (!bothEmpty && !Objects.equals(b, a)) {
        changes.add(
            new RecordHistoryEntry.Change(field, isEmpty(b) ? null : b, isEmpty(a) ? null : a));
      }
    }
    return changes;
  }

  private static boolean isEmpty(JsonNode node) {
    return node == null || node.isNull();
  }

  private static Specification<ManagementRecord> specFor(UUID associationId, RecordFilter f) {
    return (root, query, cb) -> {
      var p = new ArrayList<Predicate>();
      p.add(cb.equal(root.get("associationId"), associationId));
      p.add(cb.isNull(root.get("deletedAt")));
      Stream.of(
              eq(cb, root.get("farmerId"), f.farmerId()),
              eq(cb, root.get("propertyId"), f.propertyId()),
              eq(cb, root.get("plotId"), f.plotId()),
              eq(cb, root.get("completeness"), f.completeness()),
              eq(cb, root.get("source"), f.source()))
          .filter(Objects::nonNull)
          .forEach(p::add);
      if (f.type() != null) {
        p.add(cb.equal(root.type(), recordClass(f.type())));
      }
      if (f.cycleStatus() != null) {
        p.add(cb.equal(root.type(), Planting.class));
        p.add(cb.equal(cb.treat(root, Planting.class).get("cycleStatus"), f.cycleStatus()));
      }
      if (f.from() != null) {
        p.add(cb.greaterThanOrEqualTo(root.get("performedOn"), f.from()));
      }
      if (f.to() != null) {
        p.add(cb.lessThanOrEqualTo(root.get("performedOn"), f.to()));
      }
      if (f.hasOpenCorrection() != null) {
        var sub = query.subquery(UUID.class);
        var c = sub.from(CorrectionRequest.class);
        sub.select(c.get("recordId"))
            .where(cb.equal(c.get("status"), CorrectionRequest.Status.OPEN));
        p.add(f.hasOpenCorrection() ? root.get("id").in(sub) : cb.not(root.get("id").in(sub)));
      }
      if (f.search() != null && !f.search().isBlank()) {
        var text =
            cb.function(
                "f_unaccent",
                String.class,
                cb.literal("%" + f.search().trim().toLowerCase() + "%"));
        var farmers = query.subquery(UUID.class);
        var u = farmers.from(AppUser.class);
        farmers
            .select(u.get("id"))
            .where(cb.like(cb.function("f_unaccent", String.class, cb.lower(u.get("name"))), text));
        var props = query.subquery(UUID.class);
        var pr = props.from(Property.class);
        props
            .select(pr.get("id"))
            .where(
                cb.like(cb.function("f_unaccent", String.class, cb.lower(pr.get("name"))), text));
        p.add(cb.or(root.get("farmerId").in(farmers), root.get("propertyId").in(props)));
      }
      return cb.and(p.toArray(Predicate[]::new));
    };
  }

  private static Predicate eq(CriteriaBuilder cb, Path<?> path, Object value) {
    return value == null ? null : cb.equal(path, value);
  }

  private static Class<? extends ManagementRecord> recordClass(RecordEnums.RecordType type) {
    return switch (type) {
      case PLANTING -> Planting.class;
      case INPUT_APPLICATION -> InputApplication.class;
      case HARVEST -> Harvest.class;
      case INPUT_PURCHASE -> InputPurchase.class;
      case OUTFLOW -> Outflow.class;
    };
  }
}
