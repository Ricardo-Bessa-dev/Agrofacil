// Cria, atualiza e exclui registros aplicando as RN09–RN17; usado pelo painel e pela sincronização.
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.attachment.AttachmentRepository;
import br.edu.ifba.agrofacil.common.domain.AreaValue;
import br.edu.ifba.agrofacil.common.domain.GeoPointValue;
import br.edu.ifba.agrofacil.common.domain.QuantityValue;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.security.Role;
import br.edu.ifba.agrofacil.common.web.VersionCheck;
import br.edu.ifba.agrofacil.config.AppProperties;
import br.edu.ifba.agrofacil.correction.CorrectionRequest;
import br.edu.ifba.agrofacil.correction.CorrectionRequestRepository;
import br.edu.ifba.agrofacil.notification.NotificationService;
import br.edu.ifba.agrofacil.notification.NotificationType;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordSource;
import br.edu.ifba.agrofacil.record.RecordWrite.HarvestWrite;
import br.edu.ifba.agrofacil.record.RecordWrite.InputApplicationWrite;
import br.edu.ifba.agrofacil.record.RecordWrite.InputPurchaseWrite;
import br.edu.ifba.agrofacil.record.RecordWrite.OutflowWrite;
import br.edu.ifba.agrofacil.record.RecordWrite.PlantingWrite;
import br.edu.ifba.agrofacil.user.AppUser;
import br.edu.ifba.agrofacil.user.UserRepository;
import br.edu.ifba.agrofacil.user.UserStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Um serviço só para o painel e para o push: assim não existem duas versões das regras (doc 2
 * §1.10). Último a chegar vence (ADR-08); o histórico fica no Envers.
 */
@Service
public class RecordService {

  private final RecordRepository records;
  private final RecordRules rules;
  private final AttachmentRepository attachments;
  private final CorrectionRequestRepository corrections;
  private final AssociationService associations;
  private final NotificationService notifications;
  private final UserRepository users;
  private final AppProperties props;
  private final Clock clock;

  public RecordService(
      RecordRepository records,
      RecordRules rules,
      AttachmentRepository attachments,
      CorrectionRequestRepository corrections,
      AssociationService associations,
      NotificationService notifications,
      UserRepository users,
      AppProperties props,
      Clock clock) {
    this.records = records;
    this.rules = rules;
    this.attachments = attachments;
    this.corrections = corrections;
    this.associations = associations;
    this.notifications = notifications;
    this.users = users;
    this.props = props;
    this.clock = clock;
  }

  /**
   * Cria ou atualiza o registro com o id informado (upsert idempotente).
   *
   * @param source MOBILE (push) ou WEB (painel)
   * @return o registro salvo, com a completude recalculada
   * @throws ApiException 404 se o registro existir mas não for visível ou estiver excluído (não
   *     ressuscita); 422 VALIDATION_ERROR nas regras
   */
  @Transactional
  public ManagementRecord upsert(
      CurrentUser user, UUID id, RecordWrite write, RecordSource source) {
    var existing = records.findById(id).orElse(null);
    if (existing != null) {
      if (existing.isDeleted() || !canSee(user, existing)) {
        throw ApiException.notFound();
      }
      if (existing.type() != write.type()) {
        throw ApiException.validation(
            "type", "TYPE_CHANGE_NOT_ALLOWED", "Não é possível mudar o tipo do registro.");
      }
    }
    var placement = rules.validate(user, write, existing, today());
    UUID previousUpdatedBy = existing == null ? null : existing.getUpdatedBy();
    UUID previousPlanting = plantingOf(existing);

    var record = existing != null ? existing : newRecord(write, id);
    if (existing == null) {
      record.markCreated(user.id(), source, source == RecordSource.MOBILE ? user.deviceId() : null);
    }
    record.markUpdated(user.id());
    var property = placement.property();
    record.placeAt(
        property.getAssociationId(), property.getFarmerId(), property.getId(), placement.plotId());
    var location = write.location();
    record.setCommon(
        write.performedOn(),
        write.notes(),
        GeoPointValue.from(location),
        location == null ? null : location.capturedAt(),
        write.clientCreatedAt());
    applyFields(record, write, associations.get(property.getAssociationId()).getTarefaM2());
    record.refreshCompleteness(attachments.recordHasPhoto(id)); // RN09
    // Toca a linha-mãe para a versão subir mesmo quando só o subtipo mudou (doc 2, Parte 2).
    record.touch();
    var saved = records.saveAndFlush(record);

    refreshPlantingCycles(previousPlanting, plantingOf(saved)); // RN12
    afterUpsert(user, saved, existing != null, previousUpdatedBy);
    return saved;
  }

  /** Edição completa pelo painel (PUT), com controle de versão (doc 1 §3.2.7). */
  @Transactional
  public ManagementRecord update(CurrentUser user, UUID id, RecordWrite write) {
    var existing = findVisible(user, id);
    VersionCheck.ensure(write.version(), existing.getVersion());
    return upsert(user, id, write, RecordSource.WEB);
  }

  /**
   * Exclusão lógica. Repetir é seguro. Plantio com aplicações ou colheitas não pode ser excluído
   * (RN13).
   *
   * @throws ApiException 409 PLANTING_HAS_DEPENDENTS
   */
  @Transactional
  public ManagementRecord delete(CurrentUser user, UUID id) {
    var record =
        records.findById(id).filter(r -> canSee(user, r)).orElseThrow(ApiException::notFound);
    if (record.isDeleted()) {
      return record;
    }
    if (record instanceof Planting && records.countDependents(id) > 0) {
      throw new ApiException(ErrorCode.PLANTING_HAS_DEPENDENTS);
    }
    record.markUpdated(user.id());
    record.markDeleted(clock.instant());
    records.saveAndFlush(record);
    refreshPlantingCycles(plantingOf(record), null); // RN12: excluir a colheita final reabre
    if (user.role() == Role.COORDINATOR) {
      notifyFarmerOfChange(record, "A associação apagou um registro seu.");
    }
    return record;
  }

  /** Registro visível ao usuário e não excluído; 404 caso contrário. */
  @Transactional(readOnly = true)
  public ManagementRecord findVisible(CurrentUser user, UUID id) {
    return (user.isFarmer()
            ? records.findByIdAndFarmerIdAndDeletedAtIsNull(id, user.id())
            : records.findByIdAndAssociationIdAndDeletedAtIsNull(id, user.associationId()))
        .orElseThrow(ApiException::notFound);
  }

  /** Recalcula a completude quando um anexo do registro muda (doc 1 §2.5). */
  @Transactional
  public void refreshCompleteness(UUID recordId) {
    records
        .findById(recordId)
        .filter(r -> !r.isDeleted())
        .ifPresent(r -> {
          var before = List.copyOf(r.getMissingFields());
          r.refreshCompleteness(attachments.recordHasPhoto(recordId));
          if (!before.equals(r.getMissingFields())) {
            r.touch();
          }
        });
  }

  /** Converte para o contrato, contando os pedidos de correção abertos em lote. */
  @Transactional(readOnly = true)
  public List<RecordView> views(List<ManagementRecord> list) {
    Map<UUID, Long> open = openCorrections(list.stream().map(ManagementRecord::getId).toList());
    return list.stream()
        .map(r -> RecordMapper.toView(r, open.getOrDefault(r.getId(), 0L)))
        .toList();
  }

  /** Pedidos de correção abertos por registro. */
  public Map<UUID, Long> openCorrections(Collection<UUID> recordIds) {
    var result = new HashMap<UUID, Long>();
    if (!recordIds.isEmpty()) {
      corrections
          .countOpenByRecord(recordIds)
          .forEach(row -> result.put((UUID) row[0], (Long) row[1]));
    }
    return result;
  }

  /** "Hoje" no fuso da associação. */
  public LocalDate today() {
    return LocalDate.now(clock.withZone(props.zone()));
  }

  private boolean canSee(CurrentUser user, ManagementRecord r) {
    return user.isFarmer()
        ? r.getFarmerId().equals(user.id())
        : r.getAssociationId().equals(user.associationId());
  }

  private static ManagementRecord newRecord(RecordWrite write, UUID id) {
    return switch (write) {
      case PlantingWrite p -> new Planting(id);
      case InputApplicationWrite a -> new InputApplication(id);
      case HarvestWrite h -> new Harvest(id);
      case InputPurchaseWrite ip -> new InputPurchase(id);
      case OutflowWrite o -> new Outflow(id);
    };
  }

  /** Copia os campos do tipo, convertendo áreas para m² (RN14) e arredondando (RN28). */
  private static void applyFields(ManagementRecord record, RecordWrite write, BigDecimal tarefaM2) {
    switch (write) {
      case PlantingWrite p ->
          ((Planting) record)
              .setFields(
                  p.cropId(),
                  blankToNull(p.cropOtherName()),
                  p.variety(),
                  AreaValue.from(p.plantedArea(), tarefaM2),
                  p.propagationType(),
                  QuantityValue.from(p.propagationQuantity()),
                  p.seedOrigin(),
                  p.seedSupplier(),
                  p.expectedHarvestOn());
      case InputApplicationWrite a ->
          ((InputApplication) record)
              .setFields(
                  a.plantingId(),
                  a.inputId(),
                  blankToNull(a.inputOtherName()),
                  a.purpose(),
                  QuantityValue.from(a.quantity()),
                  AreaValue.from(a.appliedArea(), tarefaM2));
      case HarvestWrite h ->
          ((Harvest) record)
              .setFields(
                  h.plantingId(),
                  QuantityValue.from(h.quantity()),
                  AreaValue.from(h.harvestedArea(), tarefaM2),
                  Boolean.TRUE.equals(h.isFinal()));
      case InputPurchaseWrite ip ->
          ((InputPurchase) record)
              .setFields(
                  ip.inputId(),
                  blankToNull(ip.inputOtherName()),
                  QuantityValue.from(ip.quantity()),
                  ip.supplier());
      case OutflowWrite o ->
          ((Outflow) record)
              .setFields(
                  o.cropId(),
                  blankToNull(o.productOtherName()),
                  o.plantingId(),
                  QuantityValue.from(o.quantity()),
                  o.destination(),
                  o.buyerName());
    }
  }

  /** Plantio afetado pela colheita (ou nulo para os outros tipos). */
  private static UUID plantingOf(ManagementRecord r) {
    return r instanceof Harvest h ? h.getPlantingId() : null;
  }

  /** RN12: o plantio fica FINISHED se houver colheita final não excluída; senão, ACTIVE. */
  private void refreshPlantingCycles(UUID... plantingIds) {
    var ids = new LinkedHashSet<UUID>();
    for (UUID id : plantingIds) {
      if (id != null) {
        ids.add(id);
      }
    }
    for (UUID id : ids) {
      records
          .findActivePlanting(id)
          .ifPresent(planting -> {
            var lastFinal = records.lastFinalHarvestOn(id);
            if (!Objects.equals(lastFinal, planting.getFinishedOn())) {
              planting.updateCycle(lastFinal);
              records.save(planting);
            }
          });
    }
  }

  /** Notificações da RN16 e resolução automática dos pedidos de correção (RN17). */
  private void afterUpsert(
      CurrentUser user, ManagementRecord record, boolean wasExisting, UUID previousUpdatedBy) {
    if (user.role() == Role.COORDINATOR) {
      notifyFarmerOfChange(
          record,
          wasExisting
              ? "A associação alterou um registro seu."
              : "A associação registrou algo em seu nome.");
      return;
    }
    if (!wasExisting) {
      return;
    }
    var data = Map.of("recordId", record.getId().toString());
    if (previousUpdatedBy != null && !previousUpdatedBy.equals(user.id())) {
      // ADR-08: o agricultor sobrescreveu uma edição da associação; o histórico guarda as duas.
      notifications.notify(
          coordinatorsOf(record),
          NotificationType.RECORD_OVERWRITTEN_BY_FARMER,
          "Registro alterado pelo agricultor",
          "Um registro que a associação tinha mudado foi alterado de novo pelo agricultor.",
          data);
    }
    var open = corrections.findByRecordIdAndStatus(record.getId(), CorrectionRequest.Status.OPEN);
    if (!open.isEmpty()) {
      open.forEach(c -> c.resolve(user.id(), clock.instant()));
      notifications.notify(
          coordinatorsOf(record),
          NotificationType.CORRECTION_RESOLVED_BY_EDIT,
          "Pedido de correção atendido",
          "O agricultor alterou o registro que tinha um pedido de correção.",
          data);
    }
  }

  private void notifyFarmerOfChange(ManagementRecord record, String body) {
    notifications.notify(
        List.of(record.getFarmerId()),
        NotificationType.RECORD_CHANGED_BY_ASSOCIATION,
        "A associação mexeu no seu caderno",
        body,
        Map.of("recordId", record.getId().toString()));
  }

  private List<UUID> coordinatorsOf(ManagementRecord record) {
    return users
        .findByAssociationIdAndRoleAndDeletedAtIsNullOrderByName(
            record.getAssociationId(), Role.COORDINATOR)
        .stream()
        .filter(u -> u.getStatus() == UserStatus.ACTIVE)
        .map(AppUser::getId)
        .toList();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
