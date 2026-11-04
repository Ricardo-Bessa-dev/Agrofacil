// Calcula o pull incremental do agricultor a partir do sync_seq de cada tabela (doc 1 §4.8).
package br.edu.ifba.agrofacil.sync;

import br.edu.ifba.agrofacil.announcement.AnnouncementRepository;
import br.edu.ifba.agrofacil.announcement.AnnouncementService;
import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.attachment.AttachmentRepository;
import br.edu.ifba.agrofacil.attachment.AttachmentView;
import br.edu.ifba.agrofacil.catalog.CatalogService;
import br.edu.ifba.agrofacil.catalog.CatalogViews.FarmInputView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.UnitView;
import br.edu.ifba.agrofacil.catalog.CropRepository;
import br.edu.ifba.agrofacil.catalog.FarmInputRepository;
import br.edu.ifba.agrofacil.catalog.MeasurementUnitRepository;
import br.edu.ifba.agrofacil.certification.CertificationReader;
import br.edu.ifba.agrofacil.certification.CertificationRepository;
import br.edu.ifba.agrofacil.common.persistence.SyncableEntity;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.security.Role;
import br.edu.ifba.agrofacil.correction.CorrectionRequestRepository;
import br.edu.ifba.agrofacil.correction.CorrectionRequestView;
import br.edu.ifba.agrofacil.notification.NotificationRepository;
import br.edu.ifba.agrofacil.notification.NotificationService;
import br.edu.ifba.agrofacil.property.PlotRepository;
import br.edu.ifba.agrofacil.property.PlotView;
import br.edu.ifba.agrofacil.property.PropertyRepository;
import br.edu.ifba.agrofacil.property.PropertyService;
import br.edu.ifba.agrofacil.property.WorkerRepository;
import br.edu.ifba.agrofacil.property.WorkerView;
import br.edu.ifba.agrofacil.record.RecordRepository;
import br.edu.ifba.agrofacil.record.RecordService;
import br.edu.ifba.agrofacil.spg.checklist.ChecklistDtos;
import br.edu.ifba.agrofacil.spg.checklist.VisitChecklistRepository;
import br.edu.ifba.agrofacil.spg.visit.VisitRepository;
import br.edu.ifba.agrofacil.spg.visit.VisitService;
import br.edu.ifba.agrofacil.user.FarmerProfile;
import br.edu.ifba.agrofacil.user.FarmerProfileRepository;
import br.edu.ifba.agrofacil.user.Me;
import br.edu.ifba.agrofacil.user.MeService;
import br.edu.ifba.agrofacil.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Para simplificar, busca até {@code limit} linhas de cada tabela e usa como cursor o menor entre
 * os maiores sync_seq das tabelas que bateram o limite. Linhas repetidas no próximo pull são
 * inofensivas, porque o upsert no app é idempotente.
 */
@Service
public class SyncPullService {

  public static final int DEFAULT_LIMIT = 500;
  public static final int MAX_LIMIT = 1000;
  private static final String INITIAL_CURSOR = "0";

  private final UserRepository users;
  private final PropertyRepository properties;
  private final PlotRepository plots;
  private final WorkerRepository workers;
  private final CertificationRepository certifications;
  private final RecordRepository records;
  private final AttachmentRepository attachments;
  private final CorrectionRequestRepository corrections;
  private final NotificationRepository notifications;
  private final AnnouncementRepository announcements;
  private final VisitRepository visits;
  private final VisitChecklistRepository checklistItems;
  private final FarmerProfileRepository profiles;
  private final CropRepository crops;
  private final FarmInputRepository inputs;
  private final MeasurementUnitRepository units;
  private final AssociationService associations;
  private final PropertyService propertyService;
  private final RecordService recordService;
  private final CertificationReader certificationReader;
  private final NotificationService notificationService;
  private final AnnouncementService announcementService;
  private final VisitService visitService;
  private final CatalogService catalogService;
  private final MeService meService;
  private final Clock clock;

  public SyncPullService(
      UserRepository users,
      PropertyRepository properties,
      PlotRepository plots,
      WorkerRepository workers,
      CertificationRepository certifications,
      RecordRepository records,
      AttachmentRepository attachments,
      CorrectionRequestRepository corrections,
      NotificationRepository notifications,
      AnnouncementRepository announcements,
      VisitRepository visits,
      VisitChecklistRepository checklistItems,
      FarmerProfileRepository profiles,
      CropRepository crops,
      FarmInputRepository inputs,
      MeasurementUnitRepository units,
      AssociationService associations,
      PropertyService propertyService,
      RecordService recordService,
      CertificationReader certificationReader,
      NotificationService notificationService,
      AnnouncementService announcementService,
      VisitService visitService,
      CatalogService catalogService,
      MeService meService,
      Clock clock) {
    this.users = users;
    this.properties = properties;
    this.plots = plots;
    this.workers = workers;
    this.certifications = certifications;
    this.records = records;
    this.attachments = attachments;
    this.corrections = corrections;
    this.notifications = notifications;
    this.announcements = announcements;
    this.visits = visits;
    this.checklistItems = checklistItems;
    this.profiles = profiles;
    this.crops = crops;
    this.inputs = inputs;
    this.units = units;
    this.associations = associations;
    this.propertyService = propertyService;
    this.recordService = recordService;
    this.certificationReader = certificationReader;
    this.notificationService = notificationService;
    this.announcementService = announcementService;
    this.visitService = visitService;
    this.catalogService = catalogService;
    this.meService = meService;
    this.clock = clock;
  }

  /**
   * Mudanças depois do cursor, só no escopo do agricultor (doc 1 §4.8). A leitura inteira é
   * consistente (uma transação só leitura).
   *
   * @param rawCursor cursor opaco recebido do app; inválido = {@code reset}
   */
  @Transactional(readOnly = true)
  public SyncDtos.PullResponse pull(CurrentUser user, String rawCursor, int requestedLimit) {
    Instant now = clock.instant();
    int limit = Math.clamp(requestedLimit, 1, MAX_LIMIT);
    var me = users.findById(user.id()).orElseThrow();
    var cursor = SyncCursor.parse(rawCursor);
    boolean resetRequested =
        cursor.isPresent()
            && cursor.get().seq() > 0
            && me.getSyncResetAt() != null
            && cursor.get().issuedAt().isBefore(me.getSyncResetAt());
    if (cursor.isEmpty() || resetRequested) {
      return new SyncDtos.PullResponse(INITIAL_CURSOR, true, true, now, emptyChanges(), List.of());
    }
    long since = cursor.get().seq();
    var page = new PageTracker(limit, since);
    var lim = Limit.of(limit);
    var association = associations.get(user.associationId());
    var deleted = new ArrayList<SyncDtos.Tombstone>();

    var people =
        page.track(
            users.findPeopleChanges(user.id(), user.associationId(), Role.COORDINATOR, since, lim),
            u -> u.getSyncSeq());
    boolean meChanged = people.stream().anyMatch(u -> u.getId().equals(user.id()));
    boolean associationChanged =
        association.getSyncSeq() != null && association.getSyncSeq() > since;

    var props =
        alive(
            page.track(properties.findChanges(user.id(), since, lim), SyncableEntity::getSyncSeq),
            "property",
            deleted);
    var plotList =
        alive(
            page.track(plots.findChanges(user.id(), since, lim), SyncableEntity::getSyncSeq),
            "plot",
            deleted);
    var workerList =
        alive(
            page.track(workers.findChanges(user.id(), since, lim), SyncableEntity::getSyncSeq),
            "worker",
            deleted);
    var certs =
        alive(
            page.track(
                certifications.findChanges(user.id(), since, lim), SyncableEntity::getSyncSeq),
            "certification",
            deleted);
    var recs =
        alive(
            page.track(records.findChanges(user.id(), since, lim), SyncableEntity::getSyncSeq),
            "record",
            deleted);
    var groupId = profiles.findById(user.id()).map(FarmerProfile::getGroupId).orElse(null);
    var atts =
        alive(
            page.track(
                attachments.findChanges(user.id(), user.associationId(), groupId, since, lim),
                SyncableEntity::getSyncSeq),
            "attachment",
            deleted);
    var corrs = page.track(corrections.findChanges(user.id(), since, lim), c -> c.getSyncSeq());
    var notes = page.track(notifications.findChanges(user.id(), since, lim), n -> n.getSyncSeq());
    var news =
        alive(
            page.track(
                announcements.findChanges(user.associationId(), groupId, since, lim),
                SyncableEntity::getSyncSeq),
            "announcement",
            deleted);
    var visitList = page.track(visits.findChanges(user.id(), since, lim), v -> v.getSyncSeq());
    var visitTombstones = new ArrayList<java.util.UUID>();
    var visitViews = visitService.viewsForFarmer(user.id(), visitList, visitTombstones);
    visitTombstones.forEach(id -> deleted.add(new SyncDtos.Tombstone("visit", id)));
    var checklist =
        page.track(
            checklistItems.findChanges(user.associationId(), since, lim), i -> i.getSyncSeq());
    var cropList =
        page.track(crops.findChanges(user.associationId(), since, lim), c -> c.getSyncSeq());
    var inputList =
        page.track(inputs.findChanges(user.associationId(), since, lim), i -> i.getSyncSeq());
    var unitList = page.track(units.findChanges(since, lim), u -> u.getSyncSeq());
    if (associationChanged) {
      page.observe(association.getSyncSeq());
    }

    Me meView = meChanged || associationChanged ? meService.build(me) : null;
    var changes =
        new SyncDtos.Changes(
            meView,
            meView == null ? null : meView.association(),
            people.stream()
                .map(u -> new SyncDtos.Person(u.getId(), u.getName(), u.getRole()))
                .toList(),
            propertyService.views(props, user.associationId()),
            plotList.stream().map(PlotView::of).toList(),
            workerList.stream().map(WorkerView::of).toList(),
            certificationReader.views(certs, association),
            recordService.views(recs),
            atts.stream().map(AttachmentView::of).toList(),
            corrs.stream().map(CorrectionRequestView::of).toList(),
            announcementService.views(news),
            notes.stream().map(notificationService::toView).toList(),
            catalogService.cropViews(cropList),
            inputList.stream().map(FarmInputView::of).toList(),
            unitList.stream().map(UnitView::of).toList(),
            visitViews,
            checklist.stream().map(ChecklistDtos.View::of).toList());
    return new SyncDtos.PullResponse(
        new SyncCursor(page.nextCursor(), now).format(),
        page.hasMore(),
        false,
        now,
        changes,
        deleted);
  }

  /** Separa as lápides (excluídas) das linhas vivas. */
  private static <T extends SyncableEntity> List<T> alive(
      List<T> list, String entity, List<SyncDtos.Tombstone> deleted) {
    var result = new ArrayList<T>();
    for (T item : list) {
      if (item.isDeleted()) {
        deleted.add(new SyncDtos.Tombstone(entity, item.getId()));
      } else {
        result.add(item);
      }
    }
    return result;
  }

  private static SyncDtos.Changes emptyChanges() {
    return new SyncDtos.Changes(
        null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
  }

  /** Acompanha o maior sync_seq visto e as tabelas que bateram o limite. */
  static final class PageTracker {
    private final int limit;
    private long maxSeen;
    private long minOfFullTables = Long.MAX_VALUE;

    PageTracker(int limit, long since) {
      this.limit = limit;
      this.maxSeen = since;
    }

    <T> List<T> track(List<T> rows, ToLongFunction<T> seq) {
      long tableMax = rows.stream().mapToLong(seq).max().orElse(maxSeen);
      maxSeen = Math.max(maxSeen, tableMax);
      if (rows.size() >= limit) {
        minOfFullTables = Math.min(minOfFullTables, tableMax);
      }
      return rows;
    }

    void observe(long seq) {
      maxSeen = Math.max(maxSeen, seq);
    }

    boolean hasMore() {
      return minOfFullTables != Long.MAX_VALUE;
    }

    long nextCursor() {
      return hasMore() ? minOfFullTables : maxSeen;
    }
  }
}
