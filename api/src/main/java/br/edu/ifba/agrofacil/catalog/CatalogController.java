// Endpoints de leitura dos catálogos e dos municípios (doc 1 §3.13); a gestão fica no
// CatalogAdminController.
package br.edu.ifba.agrofacil.catalog;

import br.edu.ifba.agrofacil.catalog.CatalogViews.CropCategoryView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.CropView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.FarmInputView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.MunicipalityView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.UnitView;
import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Catálogos")
@RestController
public class CatalogController {

  private final CatalogService service;
  private final CurrentUserProvider currentUser;

  public CatalogController(CatalogService service, CurrentUserProvider currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @GetMapping("/catalog/crops")
  public List<CropView> crops(@RequestParam(defaultValue = "false") boolean includeInactive) {
    return service.crops(currentUser.get(), includeInactive);
  }

  @GetMapping("/catalog/crop-categories")
  public List<CropCategoryView> categories() {
    return service.categories();
  }

  @GetMapping("/catalog/farm-inputs")
  public List<FarmInputView> farmInputs(
      @RequestParam(defaultValue = "false") boolean includeInactive) {
    return service.farmInputs(currentUser.get(), includeInactive);
  }

  @GetMapping("/catalog/units")
  public List<UnitView> units() {
    return service.units();
  }

  @GetMapping("/municipalities")
  public List<MunicipalityView> municipalities(
      @RequestParam(defaultValue = "BA") String uf, @RequestParam(required = false) String search) {
    return service.municipalities(uf, search);
  }
}
