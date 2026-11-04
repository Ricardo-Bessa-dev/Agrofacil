// Consultas de leitura sobre certificações: status efetivo, certificação atual e conversão em lote.
package br.edu.ifba.agrofacil.certification;

import br.edu.ifba.agrofacil.association.Association;
import br.edu.ifba.agrofacil.attachment.Attachment;
import br.edu.ifba.agrofacil.attachment.AttachmentRepository;
import br.edu.ifba.agrofacil.config.AppProperties;
import br.edu.ifba.agrofacil.property.Property;
import br.edu.ifba.agrofacil.property.PropertyRepository;
import br.edu.ifba.agrofacil.user.AppUser;
import br.edu.ifba.agrofacil.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CertificationReader {

  private final CertificationRepository repository;
  private final PropertyRepository properties;
  private final UserRepository users;
  private final AttachmentRepository attachments;
  private final AppProperties props;
  private final Clock clock;

  public CertificationReader(
      CertificationRepository repository,
      PropertyRepository properties,
      UserRepository users,
      AttachmentRepository attachments,
      AppProperties props,
      Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.users = users;
    this.attachments = attachments;
    this.props = props;
    this.clock = clock;
  }

  /** "Hoje" no fuso da associação. */
  public LocalDate today() {
    return LocalDate.now(clock.withZone(props.zone()));
  }

  /**
   * Certificação atual de cada propriedade: a de maior {@code issued_on} que não esteja cancelada
   * nem excluída (doc 1 §2.3.7). Propriedades sem certificação ficam fora do mapa.
   */
  @Transactional(readOnly = true)
  public Map<UUID, CertificationSummary> currentByProperty(
      Collection<UUID> propertyIds, Association association) {
    var result = new HashMap<UUID, CertificationSummary>();
    currentEntities(propertyIds).forEach((id, c) -> result.put(id, summary(c, association)));
    return result;
  }

  /** Mesma regra de {@link #currentByProperty}, devolvendo as entidades. */
  @Transactional(readOnly = true)
  public Map<UUID, Certification> currentEntities(Collection<UUID> propertyIds) {
    if (propertyIds.isEmpty()) {
      return Map.of();
    }
    return pickCurrent(repository.findByPropertyIdInAndDeletedAtIsNull(propertyIds));
  }

  /** Escolhe a atual de cada propriedade numa lista de certificações não excluídas. */
  public static Map<UUID, Certification> pickCurrent(Collection<Certification> list) {
    var result = new HashMap<UUID, Certification>();
    list.stream()
        .filter(c -> !c.isDeleted() && c.getManualStatus() != ManualStatus.CANCELLED)
        .collect(Collectors.groupingBy(Certification::getPropertyId))
        .forEach((propertyId, certs) ->
            certs.stream()
                .max(Comparator.comparing(Certification::getIssuedOn))
                .ifPresent(c -> result.put(propertyId, c)));
    return result;
  }

  public CertificationSummary summary(Certification c, Association association) {
    var r = calculate(c, association);
    return new CertificationSummary(
        c.getId(), r.status(), c.getIssuedOn(), c.getExpiresOn(), r.daysToExpire());
  }

  /** Status efetivo de uma certificação hoje. */
  public CertificationStatusCalculator.Result calculate(Certification c, Association association) {
    return CertificationStatusCalculator.calculate(
        c.getManualStatus(), c.getExpiresOn(), today(), association.getAlertDays());
  }

  /**
   * Converte para o contrato em lote (propriedades, agricultores, PDFs e "atual" sem uma consulta
   * por linha).
   */
  @Transactional(readOnly = true)
  public List<CertificationView> views(List<Certification> list, Association association) {
    if (list.isEmpty()) {
      return List.of();
    }
    var propertyIds = list.stream().map(Certification::getPropertyId).distinct().toList();
    var current = currentEntities(propertyIds);
    Map<UUID, Property> propertyById =
        properties.findAllById(propertyIds).stream()
            .collect(Collectors.toMap(Property::getId, Function.identity()));
    Map<UUID, String> farmerNames =
        users
            .findByIdIn(propertyById.values().stream().map(Property::getFarmerId).toList())
            .stream()
            .collect(Collectors.toMap(AppUser::getId, AppUser::getName));
    Map<UUID, UUID> documents = latestDocuments(list.stream().map(Certification::getId).toList());
    return list.stream()
        .map(c -> {
          var cur = current.get(c.getPropertyId());
          var property = propertyById.get(c.getPropertyId());
          return toView(
              c,
              calculate(c, association),
              cur != null && cur.getId().equals(c.getId()),
              documents.get(c.getId()),
              property == null
                  ? null
                  : new CertificationView.PropertyRef(
                      property.getId(),
                      property.getName(),
                      property.getFarmerId(),
                      farmerNames.get(property.getFarmerId())));
        })
        .toList();
  }

  /** PDF mais recente de cada certificação. */
  private Map<UUID, UUID> latestDocuments(List<UUID> certificationIds) {
    var result = new HashMap<UUID, UUID>();
    attachments
        .findByOwnerTypeAndOwnerIdInAndKindAndDeletedAtIsNull(
            Attachment.OwnerType.CERTIFICATION, certificationIds, Attachment.Kind.DOCUMENT)
        .stream()
        .collect(Collectors.groupingBy(Attachment::getOwnerId))
        .forEach((owner, docs) ->
            docs.stream()
                .max(Comparator.comparing(Attachment::getCreatedAt))
                .map(Attachment::getId)
                .ifPresent(id -> result.put(owner, id)));
    return result;
  }

  private static CertificationView toView(
      Certification c,
      CertificationStatusCalculator.Result r,
      boolean isCurrent,
      UUID documentId,
      CertificationView.PropertyRef property) {
    return new CertificationView(
        c.getId(),
        c.getPropertyId(),
        c.getNumber(),
        c.getIssuedOn(),
        c.getExpiresOn(),
        c.getScope(),
        c.getProducts().stream()
            .map(p -> new CertificationWrite.Product(p.getCropId(), p.getProductName()))
            .toList(),
        c.getWorkerIds().stream().sorted().toList(),
        c.getNotes(),
        c.getPreviousId(),
        r.status(),
        r.daysToExpire(),
        c.getStatusReason(),
        isCurrent,
        documentId,
        property,
        c.getVersion());
  }
}
