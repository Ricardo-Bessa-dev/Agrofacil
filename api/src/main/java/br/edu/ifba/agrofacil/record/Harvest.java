// Colheita de um plantio; a colheita final encerra o ciclo (RN12).
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.common.domain.AreaValue;
import br.edu.ifba.agrofacil.common.domain.QuantityValue;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "harvest")
@PrimaryKeyJoinColumn(name = "record_id")
@DiscriminatorValue("HARVEST")
@Audited
public class Harvest extends ManagementRecord {

  private UUID plantingId;

  @Embedded
  @AttributeOverride(name = "value", column = @Column(name = "quantity"))
  @AttributeOverride(name = "unit", column = @Column(name = "quantity_unit"))
  private QuantityValue quantity;

  @Embedded
  @AttributeOverride(name = "value", column = @Column(name = "harvested_area"))
  @AttributeOverride(name = "unit", column = @Column(name = "harvested_area_unit"))
  @AttributeOverride(name = "m2", column = @Column(name = "harvested_area_m2"))
  private AreaValue harvestedArea;

  /** "Terminei de colher esta plantação". */
  @Column(name = "is_final")
  private boolean finalHarvest;

  protected Harvest() {}

  public Harvest(UUID id) {
    super(id);
  }

  @Override
  public RecordType type() {
    return RecordType.HARVEST;
  }

  @Override
  public List<String> missingRecommendedFields(boolean hasPhoto) {
    var missing = new ArrayList<String>();
    if (quantity == null) {
      missing.add("quantity");
    }
    return missing;
  }

  public void setFields(
      UUID plantingId, QuantityValue quantity, AreaValue harvestedArea, boolean finalHarvest) {
    this.plantingId = plantingId;
    this.quantity = quantity;
    this.harvestedArea = harvestedArea;
    this.finalHarvest = finalHarvest;
  }

  public UUID getPlantingId() {
    return plantingId;
  }

  public QuantityValue getQuantity() {
    return quantity;
  }

  public AreaValue getHarvestedArea() {
    return harvestedArea;
  }

  public boolean isFinalHarvest() {
    return finalHarvest;
  }
}
