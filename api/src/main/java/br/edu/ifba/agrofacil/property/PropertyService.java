// Cria, edita, lista e exclui propriedades pelo painel e pelo push do app (RN06, RN07).
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.association.Association;
import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.catalog.MunicipalityNames;
import br.edu.ifba.agrofacil.certification.CertificationReader;
import br.edu.ifba.agrofacil.certification.CertificationRepository;
import br.edu.ifba.agrofacil.certification.CertificationStatusCalculator;
import br.edu.ifba.agrofacil.certification.CertificationSummary;
import br.edu.ifba.agrofacil.common.domain.AddressValue;
import br.edu.ifba.agrofacil.common.domain.AreaValue;
import br.edu.ifba.agrofacil.common.domain.GeoPointValue;
import br.edu.ifba.agrofacil.common.domain.MeasureRules;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.error.FieldErrorItem;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import br.edu.ifba.agrofacil.common.web.VersionCheck;
import br.edu.ifba.agrofacil.user.AppUser;
import br.edu.ifba.agrofacil.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class PropertyService {

  private final PropertyRepository properties;
  private final PlotRepository plots;
  private final WorkerRepository workers;
  private final CertificationRepository certifications;
  private final CertificationReader certificationReader;
  private final UserRepository users;
  private final AssociationService associations;
  private final MunicipalityNames municipalities;
  private final JsonPatch jsonPatch;
  private final Clock clock;

  public PropertyService(
      PropertyRepository properties,
      PlotRepository plots,
      WorkerRepository workers,
      CertificationRepository certifications,
      CertificationReader certificationReader,
      UserRepository users,
      AssociationService associations,
      MunicipalityNames municipalities,
      JsonPatch jsonPatch,
      Clock clock) {
    this.properties = properties;
    this.plots = plots;
    this.workers = workers;
    this.certifications = certifications;
    this.certificationReader = certificationReader;
    this.users = users;
    this.associations = associations;
    this.municipalities = municipalities;
    this.jsonPatch = jsonPatch;
    this.clock = clock;
  }

  /**
   * Propriedade visível ao usuário: o agricultor só vê as próprias; o coordenador, as da
   * associação. Fora do escopo responde 404 (doc 1 §3.17).
   */
  @Transactional(readOnly = true)
  public Property findVisible(CurrentUser user, UUID id) {
    return (user.isFarmer()
            ? properties.findByIdAndFarmerIdAndDeletedAtIsNull(id, user.id())
            : properties.findByIdAndAssociationIdAndDeletedAtIsNull(id, user.associationId()))
        .orElseThrow(ApiException::notFound);
  }

  /** Lista paginada do painel, com filtro opcional pelo status da certificação atual. */
  @Transactional(readOnly = true)
  public PageResponse<PropertyView> list(CurrentUser user, PropertyFilter filter, Pageable page) {
    Specification<Property> spec = specFor(user.associationId(), filter);
    if (filter.certificationStatus() == null) {
      Page<Property> result = properties.findAll(spec, page);
      return PageResponse.of(result, views(result.getContent(), user.associationId()));
    }
    // O status é calculado, não gravado: filtra em memória (o volume é de dezenas de propriedades).
    var all = views(properties.findAll(spec, page.getSort()), user.associationId());
    var filtered =
        all.stream()
            .filter(v ->
                v.currentCertification() != null
                    && v.currentCertification().status() == filter.certificationStatus())
            .toList();
    int from = (int) Math.min(page.getOffset(), filtered.size());
    int to = Math.min(from + page.getPageSize(), filtered.size());
    return PageResponse.of(
        new PageImpl<>(filtered.subList(from, to), page, filtered.size()),
        filtered.subList(from, to));
  }

  /** Propriedades com localização, para o mapa do painel. */
  @Transactional(readOnly = true)
  public List<PropertyMapItem> map(CurrentUser user) {
    return views(properties.findWithLocation(user.associationId()), user.associationId()).stream()
        .map(v ->
            new PropertyMapItem(
                v.id(),
                v.name(),
                v.farmerName(),
                v.location().lat(),
                v.location().lng(),
                v.currentCertification() == null ? null : v.currentCertification().status()))
        .toList();
  }

  /** Detalhe com áreas, pessoas vinculadas e histórico de certificações. */
  @Transactional(readOnly = true)
  public PropertyDetail detail(CurrentUser user, UUID id) {
    var property = findVisible(user, id);
    var association = associations.get(user.associationId());
    var certs =
        certifications.findByPropertyIdInAndDeletedAtIsNull(List.of(id)).stream()
            .sorted((a, b) -> b.getIssuedOn().compareTo(a.getIssuedOn()))
            .toList();
    return new PropertyDetail(
        views(List.of(property), user.associationId()).getFirst(),
        plots.findByPropertyIdAndDeletedAtIsNullOrderByName(id).stream().map(PlotView::of).toList(),
        workers.findByPropertyIdAndDeletedAtIsNullOrderByName(id).stream()
            .map(WorkerView::of)
            .toList(),
        certificationReader.views(certs, association));
  }

  /** Criação pelo coordenador, em nome de um agricultor da associação (RN06). */
  @Transactional
  public PropertyView create(CurrentUser user, PropertyWrite write) {
    if (write.farmerId() == null) {
      throw ApiException.validation("farmerId", "REQUIRED", "Escolha o agricultor.");
    }
    var farmer = findFarmer(user, write.farmerId());
    var property = new Property(UUID.randomUUID(), user.associationId(), farmer.getId(), user.id());
    var saved = createWithMainPlot(property, write);
    return views(List.of(saved), user.associationId()).getFirst();
  }

  /** Edição parcial pelo coordenador. Trocar o dono leva junto os registros da propriedade. */
  @Transactional
  public PropertyView patch(CurrentUser user, UUID id, JsonNode body) {
    var property = findVisible(user, id);
    var current = views(List.of(property), user.associationId()).getFirst();
    var write = jsonPatch.merge(current.toWrite(), body, PropertyWrite.class);
    VersionCheck.ensure(body, write.version(), property.getVersion());
    if (write.farmerId() != null && !write.farmerId().equals(property.getFarmerId())) {
      changeOwner(user, property, findFarmer(user, write.farmerId()));
    }
    apply(property, write);
    return views(List.of(properties.saveAndFlush(property)), user.associationId()).getFirst();
  }

  /**
   * Exclusão lógica (RN07). Bloqueada se a certificação atual estiver em vigor.
   *
   * @throws ApiException 409 PROPERTY_HAS_ACTIVE_CERTIFICATION
   */
  @Transactional
  public void delete(CurrentUser user, UUID id) {
    softDelete(findVisible(user, id));
  }

  /**
   * Cria ou atualiza a propriedade enviada pelo app (push). O dono é sempre o próprio agricultor.
   *
   * @return a versão gravada
   */
  @Transactional
  public Property upsertFromFarmer(CurrentUser farmer, UUID id, PropertyWrite write) {
    var existing = properties.findById(id);
    if (existing.isEmpty()) {
      var property = new Property(id, farmer.associationId(), farmer.id(), farmer.id());
      return createWithMainPlot(property, write);
    }
    var property = existing.get();
    if (!property.getFarmerId().equals(farmer.id()) || property.isDeleted()) {
      throw ApiException.notFound(); // não ressuscita nem revela propriedade alheia
    }
    apply(property, write);
    return properties.saveAndFlush(property);
  }

  /** Exclusão pelo app; repetir é seguro (idempotente). */
  @Transactional
  public Property deleteFromFarmer(CurrentUser farmer, UUID id) {
    var property =
        properties
            .findById(id)
            .filter(p -> p.getFarmerId().equals(farmer.id()))
            .orElseThrow(ApiException::notFound);
    if (!property.isDeleted()) {
      softDelete(property);
    }
    return property;
  }

  /** Converte em lote, resolvendo donos e certificações sem uma consulta por linha. */
  @Transactional(readOnly = true)
  public List<PropertyView> views(List<Property> list, UUID associationId) {
    if (list.isEmpty()) {
      return List.of();
    }
    var association = associations.get(associationId);
    Map<UUID, String> farmerNames =
        users.findByIdIn(list.stream().map(Property::getFarmerId).distinct().toList()).stream()
            .collect(Collectors.toMap(AppUser::getId, AppUser::getName));
    Map<UUID, CertificationSummary> current =
        certificationReader.currentByProperty(
            list.stream().map(Property::getId).toList(), association);
    return list.stream()
        .map(p -> toView(p, farmerNames.get(p.getFarmerId()), current.get(p.getId())))
        .toList();
  }

  /** Resumos para a ficha do agricultor. */
  @Transactional(readOnly = true)
  public Map<UUID, List<PropertySummary>> summariesByFarmer(
      Collection<UUID> farmerIds, Association association) {
    var list = properties.findByFarmerIdInAndDeletedAtIsNull(farmerIds);
    var current =
        certificationReader.currentByProperty(
            list.stream().map(Property::getId).toList(), association);
    return list.stream()
        .collect(
            Collectors.groupingBy(
                Property::getFarmerId,
                Collectors.mapping(
                    p ->
                        new PropertySummary(
                            p.getId(), p.getName(), municipalityName(p), current.get(p.getId())),
                    Collectors.toList())));
  }

  private Property createWithMainPlot(Property property, PropertyWrite write) {
    apply(property, write);
    var saved = properties.saveAndFlush(property);
    // RN06: toda propriedade nasce com a "Área principal", do tamanho total e status a definir.
    plots.save(
        new Plot(
            saved.getId(),
            Plot.MAIN_PLOT_NAME,
            AreaValue.copyOf(saved.getTotalArea()),
            PlotStatus.NOT_DEFINED));
    return saved;
  }

  private void apply(Property property, PropertyWrite write) {
    var errors = new ArrayList<FieldErrorItem>();
    MeasureRules.checkArea("totalArea", write.totalArea()).ifPresent(errors::add);
    var municipalityId = write.address() == null ? null : write.address().municipalityId();
    if (municipalityId != null && !municipalities.exists(municipalityId)) {
      errors.add(
          new FieldErrorItem("address.municipalityId", "NOT_FOUND", "Município não encontrado."));
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    var association = associations.get(property.getAssociationId());
    property.setName(write.name().trim());
    property.setTotalArea(AreaValue.from(write.totalArea(), association.getTarefaM2()));
    property.setProductionDescription(write.productionDescription());
    property.setLocation(GeoPointValue.from(write.location()));
    property.setAddress(AddressValue.from(write.address()));
    property.setAccessDirections(write.accessDirections());
  }

  private void softDelete(Property property) {
    var association = associations.get(property.getAssociationId());
    var current =
        certificationReader
            .currentByProperty(List.of(property.getId()), association)
            .get(property.getId());
    if (current != null && CertificationStatusCalculator.isInForce(current.status())) {
      throw new ApiException(ErrorCode.PROPERTY_HAS_ACTIVE_CERTIFICATION);
    }
    property.markDeleted(clock.instant());
  }

  /** O app dos dois agricultores precisa refazer o espelho local (doc 1 §4.8). */
  private void changeOwner(CurrentUser user, Property property, AppUser newOwner) {
    var oldOwner = users.findById(property.getFarmerId()).orElseThrow();
    property.setFarmerId(newOwner.getId());
    properties.moveRecordsToFarmer(property.getId(), newOwner.getId());
    oldOwner.requestSyncReset(clock.instant());
    newOwner.requestSyncReset(clock.instant());
  }

  private AppUser findFarmer(CurrentUser user, UUID farmerId) {
    return users
        .findByIdAndAssociationIdAndDeletedAtIsNull(farmerId, user.associationId())
        .filter(AppUser::isFarmer)
        .orElseThrow(
            () -> ApiException.validation("farmerId", "NOT_FOUND", "Agricultor não encontrado."));
  }

  private PropertyView toView(Property p, String farmerName, CertificationSummary current) {
    return new PropertyView(
        p.getId(),
        p.getFarmerId(),
        farmerName,
        p.getName(),
        AreaValue.toDto(p.getTotalArea()),
        p.getProductionDescription(),
        GeoPointValue.toDto(p.getLocation(), null),
        AddressValue.toDto(p.getAddress(), municipalities::nameOf),
        p.getAccessDirections(),
        current,
        p.getCreatedAt(),
        p.getUpdatedAt(),
        p.getVersion());
  }

  private String municipalityName(Property p) {
    var id = p.getAddress() == null ? null : p.getAddress().getMunicipalityId();
    return id == null ? null : municipalities.nameOf(id);
  }

  private static Specification<Property> specFor(UUID associationId, PropertyFilter f) {
    return (root, query, cb) -> {
      var predicates = new ArrayList<Predicate>();
      predicates.add(cb.equal(root.get("associationId"), associationId));
      predicates.add(cb.isNull(root.get("deletedAt")));
      if (f.farmerId() != null) {
        predicates.add(cb.equal(root.get("farmerId"), f.farmerId()));
      }
      if (f.municipalityId() != null) {
        predicates.add(cb.equal(root.get("address").get("municipalityId"), f.municipalityId()));
      }
      if (f.search() != null && !f.search().isBlank()) {
        predicates.add(
            cb.like(
                cb.function("f_unaccent", String.class, cb.lower(root.get("name"))),
                cb.function(
                    "f_unaccent", String.class, cb.literal("%" + f.search().toLowerCase() + "%"))));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
