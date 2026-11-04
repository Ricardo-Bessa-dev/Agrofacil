// Plantio: ciclo de uma cultura numa área; aplicações e colheitas apontam para ele (ADR-05).
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.common.domain.AreaValue;
import br.edu.ifba.agrofacil.common.domain.QuantityValue;
import br.edu.ifba.agrofacil.record.RecordEnums.CycleStatus;
import br.edu.ifba.agrofacil.record.RecordEnums.PropagationType;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordType;
import br.edu.ifba.agrofacil.record.RecordEnums.SeedOrigin;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "planting")
@PrimaryKeyJoinColumn(name = "record_id")
@DiscriminatorValue("PLANTING")
@Audited
public class Planting extends ManagementRecord {

  private UUID cropId;
  private String cropOtherName; // "Outro" (ADR-07)
  private String variety;

  @Embedded
  @AttributeOverride(name = "value", column = @Column(name = "planted_area"))
  @AttributeOverride(name = "unit", column = @Column(name = "planted_area_unit"))
  @AttributeOverride(name = "m2", column = @Column(name = "planted_area_m2"))
  private AreaValue plantedArea;

  @Enumerated(EnumType.STRING)
  private PropagationType propagationType;

  @Embedded
  @AttributeOverride(name = "value", column = @Column(name = "propagation_quantity"))
  @AttributeOverride(name = "unit", column = @Column(name = "propagation_unit"))
  private QuantityValue propagationQuantity;

  @Enumerated(EnumType.STRING)
  private SeedOrigin seedOrigin;

  private String seedSupplier;
  private LocalDate expectedHarvestOn;

  @Enumerated(EnumType.STRING)
  private CycleStatus cycleStatus = CycleStatus.ACTIVE;

  private LocalDate finishedOn;

  protected Planting() {}

  public Planting(UUID id) {
    super(id);
  }

  @Override
  public RecordType type() {
    return RecordType.PLANTING;
  }

  @Override
  public List<String> missingRecommendedFields(boolean hasPhoto) {
    var missing = new ArrayList<String>();
    if (plantedArea == null) {
      missing.add("plantedArea");
    }
    if (seedOrigin == null) {
      missing.add("seedOrigin");
    }
    if (expectedHarvestOn == null) {
      missing.add("expectedHarvestOn");
    }
    return missing;
  }

  /** Encerra ou reabre o ciclo conforme as colheitas finais (RN12). */
  public void updateCycle(LocalDate lastFinalHarvestOn) {
    cycleStatus = lastFinalHarvestOn == null ? CycleStatus.ACTIVE : CycleStatus.FINISHED;
    finishedOn = lastFinalHarvestOn;
  }

  public void setFields(
      UUID cropId,
      String cropOtherName,
      String variety,
      AreaValue plantedArea,
      PropagationType propagationType,
      QuantityValue propagationQuantity,
      SeedOrigin seedOrigin,
      String seedSupplier,
      LocalDate expectedHarvestOn) {
    this.cropId = cropId;
    this.cropOtherName = cropOtherName;
    this.variety = variety;
    this.plantedArea = plantedArea;
    this.propagationType = propagationType;
    this.propagationQuantity = propagationQuantity;
    this.seedOrigin = seedOrigin;
    this.seedSupplier = seedSupplier;
    this.expectedHarvestOn = expectedHarvestOn;
  }

  /** RN22: troca o texto livre pelo item do catálogo escolhido pelo coordenador. */
  public void mapCrop(UUID catalogId) {
    this.cropId = catalogId;
    this.cropOtherName = null;
  }

  public UUID getCropId() {
    return cropId;
  }

  public String getCropOtherName() {
    return cropOtherName;
  }

  public String getVariety() {
    return variety;
  }

  public AreaValue getPlantedArea() {
    return plantedArea;
  }

  public PropagationType getPropagationType() {
    return propagationType;
  }

  public QuantityValue getPropagationQuantity() {
    return propagationQuantity;
  }

  public SeedOrigin getSeedOrigin() {
    return seedOrigin;
  }

  public String getSeedSupplier() {
    return seedSupplier;
  }

  public LocalDate getExpectedHarvestOn() {
    return expectedHarvestOn;
  }

  public CycleStatus getCycleStatus() {
    return cycleStatus;
  }

  public LocalDate getFinishedOn() {
    return finishedOn;
  }
}
