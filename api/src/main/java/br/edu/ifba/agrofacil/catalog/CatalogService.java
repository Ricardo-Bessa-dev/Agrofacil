// Leitura dos catálogos (culturas, insumos, unidades) e busca de municípios (doc 1 §3.13).
package br.edu.ifba.agrofacil.catalog;

import br.edu.ifba.agrofacil.catalog.CatalogViews.CropCategoryView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.CropView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.FarmInputView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.MunicipalityView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.UnitView;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.IdName;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A gestão dos catálogos pelo coordenador entra no marco M8 (doc 2 §1.18). */
@Service
public class CatalogService {

  private static final int MAX_MUNICIPALITIES = 50;

  private final CropRepository crops;
  private final CropCategoryRepository categories;
  private final FarmInputRepository inputs;
  private final MeasurementUnitRepository units;
  private final MunicipalityRepository municipalities;

  public CatalogService(
      CropRepository crops,
      CropCategoryRepository categories,
      FarmInputRepository inputs,
      MeasurementUnitRepository units,
      MunicipalityRepository municipalities) {
    this.crops = crops;
    this.categories = categories;
    this.inputs = inputs;
    this.units = units;
    this.municipalities = municipalities;
  }

  @Transactional(readOnly = true)
  public List<CropView> crops(CurrentUser user, boolean includeInactive) {
    return cropViews(
        crops.findVisible(user.associationId()).stream()
            .filter(c -> includeInactive || c.isActive())
            .toList());
  }

  /** Converte culturas resolvendo a categoria (usado também pelo pull). */
  @Transactional(readOnly = true)
  public List<CropView> cropViews(List<Crop> list) {
    Map<UUID, CropCategory> byId =
        categories.findAll().stream()
            .collect(Collectors.toMap(CropCategory::getId, Function.identity()));
    return list.stream()
        .map(c -> {
          var category = c.getCategoryId() == null ? null : byId.get(c.getCategoryId());
          return new CropView(
              c.getId(),
              c.getName(),
              c.getIcon(),
              category == null ? null : new IdName(category.getId(), category.getName()),
              c.getAssociationId() == null,
              c.isActive(),
              c.getVersion());
        })
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CropCategoryView> categories() {
    return categories.findAll().stream()
        .map(c -> new CropCategoryView(c.getId(), c.getName(), c.getIcon()))
        .sorted((a, b) -> a.name().compareTo(b.name()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FarmInputView> farmInputs(CurrentUser user, boolean includeInactive) {
    return inputs.findVisible(user.associationId()).stream()
        .filter(i -> includeInactive || i.isActive())
        .map(FarmInputView::of)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<UnitView> units() {
    return units.findAllByOrderBySortOrder().stream().map(UnitView::of).toList();
  }

  /** Busca por nome, sem diferenciar acentos e maiúsculas. */
  @Transactional(readOnly = true)
  public List<MunicipalityView> municipalities(String uf, String search) {
    String pattern = "%" + (search == null ? "" : search.trim()) + "%";
    return municipalities.search(uf, pattern, Limit.of(MAX_MUNICIPALITIES)).stream()
        .map(m -> new MunicipalityView(m.getId(), m.getName(), m.getUf()))
        .toList();
  }
}
