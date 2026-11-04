// Cadastro, renovação e mudança de status das certificações pelo painel (RN18, RN19, doc 1 §3.10).
package br.edu.ifba.agrofacil.certification;

import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.catalog.Crop;
import br.edu.ifba.agrofacil.catalog.CropRepository;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.FieldErrorItem;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import br.edu.ifba.agrofacil.common.web.VersionCheck;
import br.edu.ifba.agrofacil.notification.NotificationService;
import br.edu.ifba.agrofacil.notification.NotificationType;
import br.edu.ifba.agrofacil.property.Property;
import br.edu.ifba.agrofacil.property.PropertyService;
import br.edu.ifba.agrofacil.property.Worker;
import br.edu.ifba.agrofacil.property.WorkerRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Só o coordenador usa estas operações; o agricultor recebe as certificações pelo pull. */
@Service
public class CertificationService {

  /** RN18: sem data de vencimento, vale 1 ano a partir da emissão. */
  private static final int DEFAULT_VALIDITY_YEARS = 1;

  private final CertificationRepository repository;
  private final CertificationReader reader;
  private final PropertyService properties;
  private final WorkerRepository workers;
  private final CropRepository crops;
  private final AssociationService associations;
  private final NotificationService notifications;
  private final Clock clock;

  public CertificationService(
      CertificationRepository repository,
      CertificationReader reader,
      PropertyService properties,
      WorkerRepository workers,
      CropRepository crops,
      AssociationService associations,
      NotificationService notifications,
      Clock clock) {
    this.repository = repository;
    this.reader = reader;
    this.properties = properties;
    this.workers = workers;
    this.crops = crops;
    this.associations = associations;
    this.notifications = notifications;
    this.clock = clock;
  }

  /**
   * Lista do painel. O status é calculado, então o filtro é feito em memória (o volume é de dezenas
   * de propriedades). Ordem: as que vencem antes primeiro.
   */
  @Transactional(readOnly = true)
  public PageResponse<CertificationView> list(
      CurrentUser user, CertificationFilter filter, Pageable page) {
    var association = associations.get(user.associationId());
    var all = repository.findAliveInAssociation(user.associationId());
    if (filter.propertyId() != null) {
      all = all.stream().filter(c -> c.getPropertyId().equals(filter.propertyId())).toList();
    }
    var filtered =
        reader.views(all, association).stream()
            .filter(v -> !filter.onlyCurrent() || v.isCurrent())
            .filter(v -> filter.status() == null || v.status() == filter.status())
            .filter(v ->
                filter.farmerId() == null
                    || (v.property() != null && filter.farmerId().equals(v.property().farmerId())))
            .filter(v ->
                filter.expiringWithinDays() == null
                    || (CertificationStatusCalculator.isInForce(v.status())
                        && v.daysToExpire() <= filter.expiringWithinDays()))
            .sorted(
                Comparator.comparing(CertificationView::expiresOn)
                    .thenComparing(CertificationView::id))
            .toList();
    int from = (int) Math.min(page.getOffset(), filtered.size());
    int to = Math.min(from + page.getPageSize(), filtered.size());
    var content = filtered.subList(from, to);
    return PageResponse.of(new PageImpl<>(content, page, filtered.size()), content);
  }

  /** Histórico da propriedade, da mais nova para a mais antiga. */
  @Transactional(readOnly = true)
  public List<CertificationView> forProperty(CurrentUser user, UUID propertyId) {
    properties.findVisible(user, propertyId);
    return reader.views(
        repository.findByPropertyIdAndDeletedAtIsNullOrderByIssuedOnDesc(propertyId),
        associations.get(user.associationId()));
  }

  @Transactional(readOnly = true)
  public CertificationView get(CurrentUser user, UUID id) {
    return view(user, findVisible(user, id));
  }

  /** Cria uma certificação para uma propriedade da associação. */
  @Transactional
  public CertificationView create(CurrentUser user, CertificationWrite write) {
    if (write.propertyId() == null) {
      throw ApiException.validation("propertyId", "REQUIRED", "Escolha a propriedade.");
    }
    var property = properties.findVisible(user, write.propertyId());
    var cert = new Certification(property.getId(), write.issuedOn(), null, null, user.id());
    return save(user, property, cert, write);
  }

  /**
   * Renovação (RN19): cria uma nova com {@code previousId}. A anterior continua valendo até vencer.
   * Produtos e trabalhadores vazios são copiados da anterior.
   */
  @Transactional
  public CertificationView renew(CurrentUser user, UUID previousId, CertificationWrite write) {
    var previous = findVisible(user, previousId);
    if (write.propertyId() != null && !write.propertyId().equals(previous.getPropertyId())) {
      throw ApiException.validation(
          "propertyId", "MISMATCH", "A renovação é da mesma propriedade da certificação anterior.");
    }
    var property = properties.findVisible(user, previous.getPropertyId());
    var filled =
        new CertificationWrite(
            property.getId(),
            write.number(),
            write.issuedOn(),
            write.expiresOn(),
            write.scope() != null ? write.scope() : previous.getScope(),
            write.productsOrEmpty().isEmpty()
                ? previous.getProducts().stream()
                    .map(p -> new CertificationWrite.Product(p.getCropId(), p.getProductName()))
                    .toList()
                : write.products(),
            write.workerIdsOrEmpty().isEmpty()
                ? aliveWorkers(property.getId(), previous.getWorkerIds())
                : write.workerIds(),
            write.notes(),
            null);
    var cert =
        new Certification(property.getId(), write.issuedOn(), null, previous.getId(), user.id());
    return save(user, property, cert, filled);
  }

  /** Edição completa (PUT) com controle de versão. */
  @Transactional
  public CertificationView update(CurrentUser user, UUID id, CertificationWrite write) {
    var cert = findVisible(user, id);
    VersionCheck.ensure(write.version(), cert.getVersion());
    if (write.propertyId() != null && !write.propertyId().equals(cert.getPropertyId())) {
      throw ApiException.validation(
          "propertyId", "NOT_EDITABLE", "Não é possível mudar a propriedade da certificação.");
    }
    return save(user, properties.findVisible(user, cert.getPropertyId()), cert, write);
  }

  /** RN19: suspende com motivo e avisa o agricultor. */
  @Transactional
  public void suspend(CurrentUser user, UUID id, String reason) {
    var cert = findVisible(user, id);
    cert.suspend(reason.trim());
    repository.saveAndFlush(cert);
    var property = properties.findVisible(user, cert.getPropertyId());
    notifications.notify(
        List.of(property.getFarmerId()),
        NotificationType.CERTIFICATION_SUSPENDED,
        "Sua certificação foi suspensa",
        "%s: %s Fale com a associação.".formatted(property.getName(), sentence(reason)),
        Map.of(
            "certificationId", cert.getId().toString(), "propertyId", property.getId().toString()));
  }

  /** RN19: cancela com motivo. Uma cancelada deixa de ser a atual da propriedade. */
  @Transactional
  public void cancel(CurrentUser user, UUID id, String reason) {
    var cert = findVisible(user, id);
    changeKeepingCurrentInSync(cert, () -> cert.cancel(reason.trim()));
  }

  /** Só a partir de {@code SUSPENDED}. */
  @Transactional
  public void reactivate(CurrentUser user, UUID id) {
    var cert = findVisible(user, id);
    cert.reactivate();
    repository.saveAndFlush(cert);
  }

  /** Exclusão lógica, para lançamentos errados. */
  @Transactional
  public void delete(CurrentUser user, UUID id) {
    var cert = findVisible(user, id);
    changeKeepingCurrentInSync(cert, () -> cert.markDeleted(clock.instant()));
  }

  private CertificationView save(
      CurrentUser user, Property property, Certification cert, CertificationWrite write) {
    var expiresOn =
        write.expiresOn() != null
            ? write.expiresOn()
            : write.issuedOn().plusYears(DEFAULT_VALIDITY_YEARS);
    var products = validate(user, property, write, expiresOn);
    changeKeepingCurrentInSync(
        cert,
        () ->
            cert.update(
                blankToNull(write.number()),
                write.issuedOn(),
                expiresOn,
                blankToNull(write.scope()),
                blankToNull(write.notes()),
                products,
                write.workerIdsOrEmpty()));
    return view(user, cert);
  }

  /**
   * Aplica a mudança e, se a certificação atual da propriedade mudou, "toca" a anterior. Assim o
   * pull leva o novo {@code isCurrent} das duas.
   */
  private void changeKeepingCurrentInSync(Certification cert, Runnable change) {
    var propertyId = cert.getPropertyId();
    var before = reader.currentEntities(List.of(propertyId)).get(propertyId);
    change.run();
    repository.saveAndFlush(cert);
    var after = reader.currentEntities(List.of(propertyId)).get(propertyId);
    if (before != null
        && !before.getId().equals(cert.getId())
        && (after == null || !after.getId().equals(before.getId()))) {
      before.touch();
      repository.saveAndFlush(before);
    }
  }

  /** RN18 e escopo de culturas e trabalhadores; junta todos os erros numa resposta só. */
  private List<CertificationProduct> validate(
      CurrentUser user, Property property, CertificationWrite write, LocalDate expiresOn) {
    var errors = new ArrayList<FieldErrorItem>();
    if (!expiresOn.isAfter(write.issuedOn())) {
      errors.add(
          new FieldErrorItem(
              "expiresOn", "BEFORE_ISSUE", "O vencimento precisa ser depois da emissão."));
    }
    var products = write.productsOrEmpty();
    if (products.isEmpty()) {
      errors.add(new FieldErrorItem("products", "REQUIRED", "Informe ao menos um produto."));
    }
    Set<UUID> visibleCrops =
        crops.findVisible(user.associationId()).stream()
            .map(Crop::getId)
            .collect(Collectors.toSet());
    var result = new ArrayList<CertificationProduct>();
    for (int i = 0; i < products.size(); i++) {
      var p = products.get(i);
      String name = blankToNull(p.productName());
      if (p.cropId() == null && name == null) {
        errors.add(
            new FieldErrorItem(
                "products[%d]".formatted(i), "REQUIRED", "Escolha a cultura ou escreva o nome."));
      } else if (p.cropId() != null && !visibleCrops.contains(p.cropId())) {
        errors.add(
            new FieldErrorItem(
                "products[%d].cropId".formatted(i), "NOT_FOUND", "Cultura não encontrada."));
      } else {
        var product = new CertificationProduct(p.cropId(), name);
        if (!result.contains(product)) {
          result.add(product);
        }
      }
    }
    Set<UUID> propertyWorkers = workerIds(property.getId());
    var ids = write.workerIdsOrEmpty();
    for (int i = 0; i < ids.size(); i++) {
      if (!propertyWorkers.contains(ids.get(i))) {
        errors.add(
            new FieldErrorItem(
                "workerIds[%d]".formatted(i),
                "NOT_FOUND",
                "Trabalhador não encontrado nesta propriedade."));
      }
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    return result;
  }

  private Certification findVisible(CurrentUser user, UUID id) {
    var cert = repository.findByIdAndDeletedAtIsNull(id).orElseThrow(ApiException::notFound);
    properties.findVisible(user, cert.getPropertyId()); // 404 fora da associação
    return cert;
  }

  private CertificationView view(CurrentUser user, Certification cert) {
    return reader.views(List.of(cert), associations.get(user.associationId())).getFirst();
  }

  private Set<UUID> workerIds(UUID propertyId) {
    return workers.findByPropertyIdAndDeletedAtIsNullOrderByName(propertyId).stream()
        .map(Worker::getId)
        .collect(Collectors.toSet());
  }

  /** Na renovação, só copia os trabalhadores que ainda existem na propriedade. */
  private List<UUID> aliveWorkers(UUID propertyId, Set<UUID> previous) {
    var alive = workerIds(propertyId);
    return previous.stream().filter(alive::contains).sorted().toList();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  /** Garante o ponto final no motivo, para a frase da notificação. */
  private static String sentence(String reason) {
    String r = Objects.requireNonNull(reason).trim();
    return r.endsWith(".") ? r : r + ".";
  }
}
