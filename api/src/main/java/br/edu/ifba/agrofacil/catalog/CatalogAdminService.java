// Gestão dos catálogos pelo coordenador: culturas, insumos e unidades (doc 1 §3.13, ADR-07).
package br.edu.ifba.agrofacil.catalog;

import br.edu.ifba.agrofacil.catalog.CatalogViews.CropView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.FarmInputView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.UnitView;
import br.edu.ifba.agrofacil.catalog.CatalogWrites.CropWrite;
import br.edu.ifba.agrofacil.catalog.CatalogWrites.FarmInputWrite;
import br.edu.ifba.agrofacil.catalog.CatalogWrites.UnitWrite;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.common.web.VersionCheck;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Não há exclusão: um item sai de uso com {@code active = false}, porque registros antigos apontam
 * para ele. Os itens globais (semeados nas migrações) também podem ser editados enquanto houver uma
 * só associação (ADR-14).
 */
@Service
public class CatalogAdminService {

  private final CropRepository crops;
  private final CropCategoryRepository categories;
  private final FarmInputRepository inputs;
  private final MeasurementUnitRepository units;
  private final CatalogService catalog;
  private final JsonPatch jsonPatch;
  private final Clock clock;

  public CatalogAdminService(
      CropRepository crops,
      CropCategoryRepository categories,
      FarmInputRepository inputs,
      MeasurementUnitRepository units,
      CatalogService catalog,
      JsonPatch jsonPatch,
      Clock clock) {
    this.crops = crops;
    this.categories = categories;
    this.inputs = inputs;
    this.units = units;
    this.catalog = catalog;
    this.jsonPatch = jsonPatch;
    this.clock = clock;
  }

  /** Cria uma cultura da associação; o nome não pode repetir um já visível. */
  @Transactional
  public CropView createCrop(CurrentUser user, CropWrite write) {
    var crop = new Crop(user.associationId());
    applyCrop(user, crop, write);
    return catalog.cropViews(List.of(crops.saveAndFlush(crop))).getFirst();
  }

  @Transactional
  public CropView patchCrop(CurrentUser user, UUID id, JsonNode body) {
    var crop = findCrop(user, id);
    var current = catalog.cropViews(List.of(crop)).getFirst();
    var write = jsonPatch.merge(current.toWrite(), body, CropWrite.class);
    VersionCheck.ensure(body, write.version(), crop.getVersion());
    applyCrop(user, crop, write);
    return catalog.cropViews(List.of(crops.saveAndFlush(crop))).getFirst();
  }

  /** Cria um insumo da associação; o nome não pode repetir um já visível. */
  @Transactional
  public FarmInputView createFarmInput(CurrentUser user, FarmInputWrite write) {
    var input = new FarmInput(user.associationId());
    applyInput(user, input, write);
    return FarmInputView.of(inputs.saveAndFlush(input));
  }

  @Transactional
  public FarmInputView patchFarmInput(CurrentUser user, UUID id, JsonNode body) {
    var input = findInput(user, id);
    var write = jsonPatch.merge(FarmInputView.of(input).toWrite(), body, FarmInputWrite.class);
    VersionCheck.ensure(body, write.version(), input.getVersion());
    applyInput(user, input, write);
    return FarmInputView.of(inputs.saveAndFlush(input));
  }

  /** Liga, desliga ou reordena uma unidade de medida. */
  @Transactional
  public UnitView patchUnit(String code, UnitWrite write) {
    var unit = units.findById(code).orElseThrow(ApiException::notFound);
    unit.update(
        write.active() != null ? write.active() : unit.isActive(),
        write.sortOrder() != null ? write.sortOrder() : unit.getSortOrder(),
        clock.instant());
    return UnitView.of(units.saveAndFlush(unit));
  }

  /** Cultura visível à associação (dela ou global); 404 caso contrário. */
  @Transactional(readOnly = true)
  public Crop findCrop(CurrentUser user, UUID id) {
    return crops
        .findById(id)
        .filter(
            c -> c.getAssociationId() == null || c.getAssociationId().equals(user.associationId()))
        .orElseThrow(ApiException::notFound);
  }

  /** Insumo visível à associação (dela ou global); 404 caso contrário. */
  @Transactional(readOnly = true)
  public FarmInput findInput(CurrentUser user, UUID id) {
    return inputs
        .findById(id)
        .filter(
            i -> i.getAssociationId() == null || i.getAssociationId().equals(user.associationId()))
        .orElseThrow(ApiException::notFound);
  }

  private void applyCrop(CurrentUser user, Crop crop, CropWrite write) {
    String name = write.name().trim();
    boolean taken =
        crops.findVisible(user.associationId()).stream()
            .anyMatch(c -> !c.getId().equals(crop.getId()) && sameName(c.getName(), name));
    if (taken) {
      throw ApiException.validation("name", "DUPLICATE", "Já existe uma cultura com este nome.");
    }
    if (write.categoryId() != null && !categories.existsById(write.categoryId())) {
      throw ApiException.validation("categoryId", "NOT_FOUND", "Categoria não encontrada.");
    }
    crop.update(
        name, blankToNull(write.icon()), write.categoryId(), !Boolean.FALSE.equals(write.active()));
  }

  private void applyInput(CurrentUser user, FarmInput input, FarmInputWrite write) {
    String name = write.name().trim();
    boolean taken =
        inputs.findVisible(user.associationId()).stream()
            .anyMatch(i -> !i.getId().equals(input.getId()) && sameName(i.getName(), name));
    if (taken) {
      throw ApiException.validation("name", "DUPLICATE", "Já existe um insumo com este nome.");
    }
    input.update(
        name,
        write.inputType(),
        blankToNull(write.regulatoryReference()),
        Boolean.TRUE.equals(write.needsAuthorization()),
        Boolean.TRUE.equals(write.homeMade()),
        !Boolean.FALSE.equals(write.active()));
  }

  private static boolean sameName(String a, String b) {
    return Objects.equals(a.trim().toLowerCase(Locale.ROOT), b.trim().toLowerCase(Locale.ROOT));
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
