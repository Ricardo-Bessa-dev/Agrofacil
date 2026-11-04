// Registro enviado pelo painel ou pelo push: união discriminada pelo campo type (doc 1 §3.9.1).
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.common.domain.Area;
import br.edu.ifba.agrofacil.common.domain.GeoPoint;
import br.edu.ifba.agrofacil.common.domain.Quantity;
import br.edu.ifba.agrofacil.record.RecordEnums.ApplicationPurpose;
import br.edu.ifba.agrofacil.record.RecordEnums.OutflowDestination;
import br.edu.ifba.agrofacil.record.RecordEnums.PropagationType;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordType;
import br.edu.ifba.agrofacil.record.RecordEnums.SeedOrigin;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Os campos somente leitura do contrato (número, completude, autoria…) não existem aqui: se vierem,
 * são ignorados. As regras que dependem do banco ficam no {@link RecordRules}.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = RecordWrite.PlantingWrite.class, name = "PLANTING"),
  @JsonSubTypes.Type(value = RecordWrite.InputApplicationWrite.class, name = "INPUT_APPLICATION"),
  @JsonSubTypes.Type(value = RecordWrite.HarvestWrite.class, name = "HARVEST"),
  @JsonSubTypes.Type(value = RecordWrite.InputPurchaseWrite.class, name = "INPUT_PURCHASE"),
  @JsonSubTypes.Type(value = RecordWrite.OutflowWrite.class, name = "OUTFLOW")
})
@Schema(
    oneOf = {
      RecordWrite.PlantingWrite.class,
      RecordWrite.InputApplicationWrite.class,
      RecordWrite.HarvestWrite.class,
      RecordWrite.InputPurchaseWrite.class,
      RecordWrite.OutflowWrite.class
    },
    discriminatorProperty = "type",
    discriminatorMapping = {
      @DiscriminatorMapping(value = "PLANTING", schema = RecordWrite.PlantingWrite.class),
      @DiscriminatorMapping(
          value = "INPUT_APPLICATION",
          schema = RecordWrite.InputApplicationWrite.class),
      @DiscriminatorMapping(value = "HARVEST", schema = RecordWrite.HarvestWrite.class),
      @DiscriminatorMapping(
          value = "INPUT_PURCHASE",
          schema = RecordWrite.InputPurchaseWrite.class),
      @DiscriminatorMapping(value = "OUTFLOW", schema = RecordWrite.OutflowWrite.class)
    })
public sealed interface RecordWrite {

  /** Tamanho máximo das observações (doc 1 §2.3.8). */
  int NOTES_MAX = 2000;

  UUID id();

  RecordType type();

  UUID propertyId();

  UUID plotId();

  LocalDate performedOn();

  String notes();

  GeoPoint location();

  Instant clientCreatedAt();

  /** Versão conhecida pelo painel; o app não usa (ADR-08). */
  Long version();

  @Schema(name = "PlantingWrite")
  record PlantingWrite(
      UUID id,
      @NotNull RecordType type,
      @NotNull UUID propertyId,
      UUID plotId,
      @NotNull LocalDate performedOn,
      @Size(max = NOTES_MAX) String notes,
      @Valid GeoPoint location,
      Instant clientCreatedAt,
      Long version,
      UUID cropId,
      @Size(max = 80) String cropOtherName,
      @Size(max = 80) String variety,
      @Valid Area plantedArea,
      PropagationType propagationType,
      @Valid Quantity propagationQuantity,
      SeedOrigin seedOrigin,
      @Size(max = 120) String seedSupplier,
      LocalDate expectedHarvestOn)
      implements RecordWrite {}

  @Schema(name = "InputApplicationWrite")
  record InputApplicationWrite(
      UUID id,
      @NotNull RecordType type,
      @NotNull UUID propertyId,
      UUID plotId,
      @NotNull LocalDate performedOn,
      @Size(max = NOTES_MAX) String notes,
      @Valid GeoPoint location,
      Instant clientCreatedAt,
      Long version,
      UUID plantingId,
      UUID inputId,
      @Size(max = 80) String inputOtherName,
      ApplicationPurpose purpose,
      @Valid Quantity quantity,
      @Valid Area appliedArea)
      implements RecordWrite {}

  @Schema(name = "HarvestWrite")
  record HarvestWrite(
      UUID id,
      @NotNull RecordType type,
      @NotNull UUID propertyId,
      UUID plotId,
      @NotNull LocalDate performedOn,
      @Size(max = NOTES_MAX) String notes,
      @Valid GeoPoint location,
      Instant clientCreatedAt,
      Long version,
      @NotNull UUID plantingId,
      @Valid Quantity quantity,
      @Valid Area harvestedArea,
      Boolean isFinal)
      implements RecordWrite {}

  @Schema(name = "InputPurchaseWrite")
  record InputPurchaseWrite(
      UUID id,
      @NotNull RecordType type,
      @NotNull UUID propertyId,
      UUID plotId,
      @NotNull LocalDate performedOn,
      @Size(max = NOTES_MAX) String notes,
      @Valid GeoPoint location,
      Instant clientCreatedAt,
      Long version,
      UUID inputId,
      @Size(max = 80) String inputOtherName,
      @Valid Quantity quantity,
      @Size(max = 120) String supplier)
      implements RecordWrite {}

  @Schema(name = "OutflowWrite")
  record OutflowWrite(
      UUID id,
      @NotNull RecordType type,
      @NotNull UUID propertyId,
      UUID plotId,
      @NotNull LocalDate performedOn,
      @Size(max = NOTES_MAX) String notes,
      @Valid GeoPoint location,
      Instant clientCreatedAt,
      Long version,
      UUID cropId,
      @Size(max = 80) String productOtherName,
      UUID plantingId,
      @Valid Quantity quantity,
      OutflowDestination destination,
      @Size(max = 120) String buyerName)
      implements RecordWrite {}
}
