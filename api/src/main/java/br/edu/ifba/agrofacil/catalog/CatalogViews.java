// Formatos dos catálogos no contrato (doc 1 §3.13).
package br.edu.ifba.agrofacil.catalog;

import br.edu.ifba.agrofacil.common.domain.Decimals;
import br.edu.ifba.agrofacil.common.web.IdName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class CatalogViews {

  private CatalogViews() {}

  /**
   * @param global true = veio da semente inicial, não da associação
   */
  public record CropView(
      UUID id,
      String name,
      String icon,
      IdName category,
      boolean global,
      boolean active,
      Long version) {

    public CatalogWrites.CropWrite toWrite() {
      return new CatalogWrites.CropWrite(
          name, icon, category == null ? null : category.id(), active, version);
    }
  }

  public record CropCategoryView(UUID id, String name, String icon) {}

  public record FarmInputView(
      UUID id,
      String name,
      InputType inputType,
      String regulatoryReference,
      boolean needsAuthorization,
      boolean homeMade,
      boolean active,
      Long version) {

    public static FarmInputView of(FarmInput i) {
      return new FarmInputView(
          i.getId(),
          i.getName(),
          i.getInputType(),
          i.getRegulatoryReference(),
          i.isNeedsAuthorization(),
          i.isHomeMade(),
          i.isActive(),
          i.getVersion());
    }

    public CatalogWrites.FarmInputWrite toWrite() {
      return new CatalogWrites.FarmInputWrite(
          name, inputType, regulatoryReference, needsAuthorization, homeMade, active, version);
    }
  }

  public record UnitView(
      String code,
      String nameSingular,
      String namePlural,
      MeasurementUnit.Kind kind,
      BigDecimal toBaseFactor,
      int sortOrder,
      boolean active) {

    public static UnitView of(MeasurementUnit u) {
      return new UnitView(
          u.getCode(),
          u.getNameSingular(),
          u.getNamePlural(),
          u.getKind(),
          Decimals.round(u.getToBaseFactor()),
          u.getSortOrder(),
          u.isActive());
    }
  }

  public record MunicipalityView(Integer id, String name, String uf) {}

  /**
   * Texto livre ainda não associado ao catálogo (RN22).
   *
   * @param occurrences quantos registros usam o texto
   * @param lastUsedOn data do registro mais recente com o texto
   */
  public record UnmappedItem(
      String text, CatalogWrites.UnmappedKind kind, long occurrences, LocalDate lastUsedOn) {}

  public record ResolveResult(int updatedRecords) {}
}
