// Registro no formato do contrato: campos comuns + específicos do tipo (doc 1 §3.9.1).
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.common.domain.Area;
import br.edu.ifba.agrofacil.common.domain.GeoPoint;
import br.edu.ifba.agrofacil.common.domain.Quantity;
import br.edu.ifba.agrofacil.record.RecordEnums.ApplicationPurpose;
import br.edu.ifba.agrofacil.record.RecordEnums.Completeness;
import br.edu.ifba.agrofacil.record.RecordEnums.CycleStatus;
import br.edu.ifba.agrofacil.record.RecordEnums.OutflowDestination;
import br.edu.ifba.agrofacil.record.RecordEnums.PropagationType;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordSource;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordType;
import br.edu.ifba.agrofacil.record.RecordEnums.SeedOrigin;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Os campos comuns ficam em {@link Common} e saem "achatados" no JSON. */
@Schema(
    oneOf = {
      RecordView.PlantingView.class,
      RecordView.InputApplicationView.class,
      RecordView.HarvestView.class,
      RecordView.InputPurchaseView.class,
      RecordView.OutflowView.class
    },
    discriminatorProperty = "type",
    discriminatorMapping = {
      @DiscriminatorMapping(value = "PLANTING", schema = RecordView.PlantingView.class),
      @DiscriminatorMapping(
          value = "INPUT_APPLICATION",
          schema = RecordView.InputApplicationView.class),
      @DiscriminatorMapping(value = "HARVEST", schema = RecordView.HarvestView.class),
      @DiscriminatorMapping(value = "INPUT_PURCHASE", schema = RecordView.InputPurchaseView.class),
      @DiscriminatorMapping(value = "OUTFLOW", schema = RecordView.OutflowView.class)
    })
public sealed interface RecordView {

  Common common();

  /**
   * @param number "Nº 000123"; nulo até o servidor receber
   * @param farmerId sempre o dono da propriedade
   * @param missingFields ex.: {@code ["plantedArea", "seedOrigin"]}
   */
  record Common(
      UUID id,
      RecordType type,
      Long number,
      UUID farmerId,
      UUID propertyId,
      UUID plotId,
      LocalDate performedOn,
      String notes,
      GeoPoint location,
      Instant clientCreatedAt,
      UUID deviceId,
      RecordSource source,
      UUID createdById,
      UUID updatedById,
      Completeness completeness,
      List<String> missingFields,
      long openCorrectionRequests,
      Instant createdAt,
      Instant updatedAt,
      Long version) {}

  @Schema(name = "PlantingRecord")
  record PlantingView(
      @JsonUnwrapped Common common,
      UUID cropId,
      String cropOtherName,
      String variety,
      Area plantedArea,
      PropagationType propagationType,
      Quantity propagationQuantity,
      SeedOrigin seedOrigin,
      String seedSupplier,
      LocalDate expectedHarvestOn,
      CycleStatus cycleStatus,
      LocalDate finishedOn)
      implements RecordView {}

  @Schema(name = "InputApplicationRecord")
  record InputApplicationView(
      @JsonUnwrapped Common common,
      UUID plantingId,
      UUID inputId,
      String inputOtherName,
      ApplicationPurpose purpose,
      Quantity quantity,
      Area appliedArea)
      implements RecordView {}

  @Schema(name = "HarvestRecord")
  record HarvestView(
      @JsonUnwrapped Common common,
      UUID plantingId,
      Quantity quantity,
      Area harvestedArea,
      boolean isFinal)
      implements RecordView {}

  @Schema(name = "InputPurchaseRecord")
  record InputPurchaseView(
      @JsonUnwrapped Common common,
      UUID inputId,
      String inputOtherName,
      Quantity quantity,
      String supplier)
      implements RecordView {}

  @Schema(name = "OutflowRecord")
  record OutflowView(
      @JsonUnwrapped Common common,
      UUID cropId,
      String productOtherName,
      UUID plantingId,
      Quantity quantity,
      OutflowDestination destination,
      String buyerName)
      implements RecordView {}
}
