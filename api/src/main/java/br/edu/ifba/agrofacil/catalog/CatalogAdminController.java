// Endpoints do coordenador para manter os catálogos e associar os itens sem catálogo (doc 1 §3.13).
package br.edu.ifba.agrofacil.catalog;

import br.edu.ifba.agrofacil.catalog.CatalogViews.CropView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.FarmInputView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.ResolveResult;
import br.edu.ifba.agrofacil.catalog.CatalogViews.UnitView;
import br.edu.ifba.agrofacil.catalog.CatalogViews.UnmappedItem;
import br.edu.ifba.agrofacil.catalog.CatalogWrites.CropWrite;
import br.edu.ifba.agrofacil.catalog.CatalogWrites.FarmInputWrite;
import br.edu.ifba.agrofacil.catalog.CatalogWrites.ResolveUnmapped;
import br.edu.ifba.agrofacil.catalog.CatalogWrites.UnitWrite;
import br.edu.ifba.agrofacil.catalog.CatalogWrites.UnmappedKind;
import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Só o coordenador (regra geral do {@code SecurityConfig}). Não existe DELETE: use active. */
@Tag(name = "Catálogos")
@RestController
public class CatalogAdminController {

  private final CatalogAdminService admin;
  private final UnmappedItemService unmapped;
  private final CurrentUserProvider currentUser;

  public CatalogAdminController(
      CatalogAdminService admin, UnmappedItemService unmapped, CurrentUserProvider currentUser) {
    this.admin = admin;
    this.unmapped = unmapped;
    this.currentUser = currentUser;
  }

  @PostMapping("/catalog/crops")
  @ResponseStatus(HttpStatus.CREATED)
  public CropView createCrop(@Valid @RequestBody CropWrite body) {
    return admin.createCrop(currentUser.get(), body);
  }

  @PatchMapping("/catalog/crops/{id}")
  public CropView patchCrop(
      @PathVariable UUID id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = CropWrite.class)))
          @RequestBody
          JsonNode body) {
    return admin.patchCrop(currentUser.get(), id, body);
  }

  @PostMapping("/catalog/farm-inputs")
  @ResponseStatus(HttpStatus.CREATED)
  public FarmInputView createFarmInput(@Valid @RequestBody FarmInputWrite body) {
    return admin.createFarmInput(currentUser.get(), body);
  }

  @PatchMapping("/catalog/farm-inputs/{id}")
  public FarmInputView patchFarmInput(
      @PathVariable UUID id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = FarmInputWrite.class)))
          @RequestBody
          JsonNode body) {
    return admin.patchFarmInput(currentUser.get(), id, body);
  }

  @PatchMapping("/catalog/units/{code}")
  public UnitView patchUnit(@PathVariable String code, @Valid @RequestBody UnitWrite body) {
    return admin.patchUnit(code, body);
  }

  @GetMapping("/catalog/unmapped")
  public List<UnmappedItem> unmapped(@RequestParam(required = false) UnmappedKind kind) {
    return unmapped.list(currentUser.get(), kind);
  }

  @PostMapping("/catalog/unmapped/resolve")
  public ResolveResult resolve(@Valid @RequestBody ResolveUnmapped body) {
    return unmapped.resolve(currentUser.get(), body);
  }
}
