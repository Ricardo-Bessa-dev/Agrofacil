// Regras de validação dos registros que dependem de datas e do banco (RN10, RN11, RN14, RN28).
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.catalog.CropRepository;
import br.edu.ifba.agrofacil.catalog.FarmInputRepository;
import br.edu.ifba.agrofacil.catalog.MeasurementUnit;
import br.edu.ifba.agrofacil.catalog.MeasurementUnitRepository;
import br.edu.ifba.agrofacil.common.domain.Area;
import br.edu.ifba.agrofacil.common.domain.MeasureRules;
import br.edu.ifba.agrofacil.common.domain.Quantity;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.FieldErrorItem;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.property.PlotRepository;
import br.edu.ifba.agrofacil.property.Property;
import br.edu.ifba.agrofacil.property.PropertyRepository;
import br.edu.ifba.agrofacil.record.RecordWrite.HarvestWrite;
import br.edu.ifba.agrofacil.record.RecordWrite.InputApplicationWrite;
import br.edu.ifba.agrofacil.record.RecordWrite.InputPurchaseWrite;
import br.edu.ifba.agrofacil.record.RecordWrite.OutflowWrite;
import br.edu.ifba.agrofacil.record.RecordWrite.PlantingWrite;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Confere o que a Bean Validation não alcança e resolve onde o registro fica: propriedade, área e
 * plantio, sempre dentro do escopo do usuário.
 */
@Component
public class RecordRules {

  /** Nenhum registro é anterior a esta data (RN10). */
  public static final LocalDate OLDEST_DATE = LocalDate.of(2000, 1, 1);

  /** Tolerância para o fuso e o relógio do aparelho (RN10). */
  public static final int FUTURE_TOLERANCE_DAYS = 1;

  private final PropertyRepository properties;
  private final PlotRepository plots;
  private final RecordRepository records;
  private final CropRepository crops;
  private final FarmInputRepository inputs;
  private final MeasurementUnitRepository units;

  public RecordRules(
      PropertyRepository properties,
      PlotRepository plots,
      RecordRepository records,
      CropRepository crops,
      FarmInputRepository inputs,
      MeasurementUnitRepository units) {
    this.properties = properties;
    this.plots = plots;
    this.records = records;
    this.crops = crops;
    this.inputs = inputs;
    this.units = units;
  }

  /**
   * Onde o registro fica, já resolvido.
   *
   * @param planting plantio referenciado (aplicação, colheita e, opcionalmente, saída)
   */
  public record Placement(Property property, UUID plotId, Planting planting) {}

  /**
   * Valida o registro e resolve propriedade, área e plantio (RN11, RN27).
   *
   * @param existing registro atual, ou {@code null} na criação
   * @throws ApiException 422 VALIDATION_ERROR com todos os campos a corrigir
   */
  public Placement validate(
      CurrentUser user, RecordWrite w, ManagementRecord existing, LocalDate today) {
    var errors = new ArrayList<FieldErrorItem>(checkDate("performedOn", w.performedOn(), today));
    checkRequiredParts(w).forEach(errors::add);
    checkMeasures(w, errors);
    checkCatalog(user, w, existing, errors);

    var property = visibleProperty(user, w.propertyId());
    if (property.isEmpty()) {
      errors.add(field("propertyId", "PROPERTY_NOT_FOUND", "Propriedade não encontrada."));
      throw ApiException.validation(errors);
    }
    Planting planting = resolvePlanting(w, property.get(), errors);
    UUID plotId = resolvePlot(w, property.get(), planting, errors);

    if (planting != null
        && w.performedOn() != null
        && !(w instanceof OutflowWrite)
        && w.performedOn().isBefore(planting.getPerformedOn())) {
      errors.add(field("performedOn", "BEFORE_PLANTING", "A data não pode ser antes do plantio."));
    }
    if (w instanceof PlantingWrite p
        && p.expectedHarvestOn() != null
        && p.performedOn() != null
        && p.expectedHarvestOn().isBefore(p.performedOn())) {
      errors.add(
          field(
              "expectedHarvestOn",
              "BEFORE_PLANTING",
              "A previsão de colheita não pode ser antes do plantio."));
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    return new Placement(property.get(), plotId, planting);
  }

  /** RN10: nem no futuro (com 1 dia de tolerância) nem antes de 2000. Função pura. */
  public static List<FieldErrorItem> checkDate(String fieldName, LocalDate date, LocalDate today) {
    if (date == null) {
      return List.of();
    }
    if (date.isAfter(today.plusDays(FUTURE_TOLERANCE_DAYS))) {
      return List.of(field(fieldName, "DATE_IN_FUTURE", "A data não pode ser depois de hoje."));
    }
    if (date.isBefore(OLDEST_DATE)) {
      return List.of(field(fieldName, "DATE_TOO_OLD", "A data não pode ser antes de 2000."));
    }
    return List.of();
  }

  /** Campos "um ou outro" obrigatórios para salvar (doc 1 §2.5). Função pura. */
  public static List<FieldErrorItem> checkRequiredParts(RecordWrite w) {
    var errors = new ArrayList<FieldErrorItem>();
    switch (w) {
      case PlantingWrite p -> {
        if (p.plotId() == null) {
          errors.add(required("plotId", "Escolha a área."));
        }
        if (p.cropId() == null && isBlank(p.cropOtherName())) {
          errors.add(required("cropId", "Escolha a cultura."));
        }
      }
      case InputApplicationWrite a -> {
        if (a.plantingId() == null && a.plotId() == null) {
          errors.add(required("plantingId", "Escolha a plantação ou a área."));
        }
        if (a.inputId() == null && isBlank(a.inputOtherName())) {
          errors.add(required("inputId", "Escolha o adubo ou remédio."));
        }
      }
      case HarvestWrite h -> {
        if (h.plantingId() == null) {
          errors.add(required("plantingId", "Escolha a plantação."));
        }
      }
      case InputPurchaseWrite ip -> {
        if (ip.inputId() == null && isBlank(ip.inputOtherName())) {
          errors.add(required("inputId", "Escolha o que foi comprado."));
        }
      }
      case OutflowWrite o -> {
        if (o.cropId() == null && isBlank(o.productOtherName())) {
          errors.add(required("cropId", "Escolha o produto."));
        }
      }
    }
    return errors;
  }

  private void checkMeasures(RecordWrite w, List<FieldErrorItem> errors) {
    Map<String, MeasurementUnit> unitByCode =
        units.findAll().stream()
            .collect(Collectors.toMap(MeasurementUnit::getCode, Function.identity()));
    switch (w) {
      case PlantingWrite p -> {
        area("plantedArea", p.plantedArea(), errors);
        quantity("propagationQuantity", p.propagationQuantity(), unitByCode, errors);
      }
      case InputApplicationWrite a -> {
        area("appliedArea", a.appliedArea(), errors);
        quantity("quantity", a.quantity(), unitByCode, errors);
      }
      case HarvestWrite h -> {
        area("harvestedArea", h.harvestedArea(), errors);
        quantity("quantity", h.quantity(), unitByCode, errors);
      }
      case InputPurchaseWrite ip -> quantity("quantity", ip.quantity(), unitByCode, errors);
      case OutflowWrite o -> quantity("quantity", o.quantity(), unitByCode, errors);
    }
  }

  /** Item do catálogo existente e ativo; um item que já estava gravado continua aceito. */
  private void checkCatalog(
      CurrentUser user, RecordWrite w, ManagementRecord existing, List<FieldErrorItem> errors) {
    switch (w) {
      case PlantingWrite p ->
          crop(user, p.cropId(), existing instanceof Planting e ? e.getCropId() : null, errors);
      case OutflowWrite o ->
          crop(user, o.cropId(), existing instanceof Outflow e ? e.getCropId() : null, errors);
      case InputApplicationWrite a ->
          input(
              user,
              a.inputId(),
              existing instanceof InputApplication e ? e.getInputId() : null,
              errors);
      case InputPurchaseWrite ip ->
          input(
              user,
              ip.inputId(),
              existing instanceof InputPurchase e ? e.getInputId() : null,
              errors);
      case HarvestWrite h -> {}
    }
  }

  private void crop(CurrentUser user, UUID id, UUID previous, List<FieldErrorItem> errors) {
    if (id == null || id.equals(previous)) {
      return;
    }
    var crop =
        crops
            .findById(id)
            .filter(c ->
                c.getAssociationId() == null || c.getAssociationId().equals(user.associationId()));
    if (crop.isEmpty() || !crop.get().isActive()) {
      errors.add(
          field(
              "cropId",
              "CATALOG_ITEM_INACTIVE",
              "Esta cultura não está disponível. Use \"Outro\"."));
    }
  }

  private void input(CurrentUser user, UUID id, UUID previous, List<FieldErrorItem> errors) {
    if (id == null || id.equals(previous)) {
      return;
    }
    var input =
        inputs
            .findById(id)
            .filter(i ->
                i.getAssociationId() == null || i.getAssociationId().equals(user.associationId()));
    if (input.isEmpty() || !input.get().isActive()) {
      errors.add(
          field(
              "inputId",
              "CATALOG_ITEM_INACTIVE",
              "Este insumo não está disponível. Use \"Outro\"."));
    }
  }

  private Optional<Property> visibleProperty(CurrentUser user, UUID propertyId) {
    if (propertyId == null) {
      return Optional.empty();
    }
    return user.isFarmer()
        ? properties.findByIdAndFarmerIdAndDeletedAtIsNull(propertyId, user.id())
        : properties.findByIdAndAssociationIdAndDeletedAtIsNull(propertyId, user.associationId());
  }

  /**
   * O plantio precisa ser da mesma propriedade e não estar excluído; pode estar encerrado (RN11).
   */
  private Planting resolvePlanting(RecordWrite w, Property property, List<FieldErrorItem> errors) {
    UUID plantingId =
        switch (w) {
          case InputApplicationWrite a -> a.plantingId();
          case HarvestWrite h -> h.plantingId();
          case OutflowWrite o -> o.plantingId();
          default -> null;
        };
    if (plantingId == null) {
      return null;
    }
    var planting =
        records
            .findActivePlanting(plantingId)
            .filter(p -> p.getPropertyId().equals(property.getId()));
    if (planting.isEmpty()) {
      errors.add(field("plantingId", "PLANTING_NOT_FOUND", "Plantação não encontrada."));
      return null;
    }
    return planting.get();
  }

  /**
   * Área do registro: no plantio e na aplicação sem plantio vem do cliente; na aplicação e na
   * colheita com plantio é copiada dele (RN11); compra e saída não têm área (RN27).
   */
  private UUID resolvePlot(
      RecordWrite w, Property property, Planting planting, List<FieldErrorItem> errors) {
    if (w instanceof InputPurchaseWrite || w instanceof OutflowWrite) {
      return null;
    }
    if (planting != null) {
      return planting.getPlotId();
    }
    if (w.plotId() == null) {
      return null; // o erro REQUIRED já foi registrado
    }
    boolean ok =
        plots
            .findByIdAndDeletedAtIsNull(w.plotId())
            .filter(p -> Objects.equals(p.getPropertyId(), property.getId()))
            .isPresent();
    if (!ok) {
      errors.add(field("plotId", "PLOT_NOT_FOUND", "Área não encontrada nesta propriedade."));
    }
    return w.plotId();
  }

  private static void area(String name, Area area, List<FieldErrorItem> errors) {
    MeasureRules.checkArea(name, area).ifPresent(errors::add);
  }

  private static void quantity(
      String name, Quantity q, Map<String, MeasurementUnit> units, List<FieldErrorItem> errors) {
    if (q == null) {
      return;
    }
    var unit = units.get(q.unit());
    if (unit == null || unit.getKind() == MeasurementUnit.Kind.AREA) {
      errors.add(
          field(
              name + ".unit", MeasureRules.UNIT_NOT_ALLOWED, "Escolha uma unidade de quantidade."));
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static FieldErrorItem required(String name, String message) {
    return field(name, "REQUIRED", message);
  }

  private static FieldErrorItem field(String name, String code, String message) {
    return new FieldErrorItem(name, code, message);
  }
}
