// Saída ou venda ("Vendi ou entreguei"), exigida pela Portaria 52, art. 11; da propriedade (RN27).
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.common.domain.QuantityValue;
import br.edu.ifba.agrofacil.record.RecordEnums.OutflowDestination;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "outflow")
@PrimaryKeyJoinColumn(name = "record_id")
@DiscriminatorValue("OUTFLOW")
@Audited
public class Outflow extends ManagementRecord {

  private UUID cropId;
  private String productOtherName;
  private UUID plantingId; // opcional

  @Embedded
  @AttributeOverride(name = "value", column = @Column(name = "quantity"))
  @AttributeOverride(name = "unit", column = @Column(name = "quantity_unit"))
  private QuantityValue quantity;

  @Enumerated(EnumType.STRING)
  private OutflowDestination destination;

  private String buyerName;

  protected Outflow() {}

  public Outflow(UUID id) {
    super(id);
  }

  @Override
  public RecordType type() {
    return RecordType.OUTFLOW;
  }

  @Override
  public List<String> missingRecommendedFields(boolean hasPhoto) {
    var missing = new ArrayList<String>();
    if (quantity == null) {
      missing.add("quantity");
    }
    if (destination == null) {
      missing.add("destination");
    }
    return missing;
  }

  public void setFields(
      UUID cropId,
      String productOtherName,
      UUID plantingId,
      QuantityValue quantity,
      OutflowDestination destination,
      String buyerName) {
    this.cropId = cropId;
    this.productOtherName = productOtherName;
    this.plantingId = plantingId;
    this.quantity = quantity;
    this.destination = destination;
    this.buyerName = buyerName;
  }

  /** RN22: troca o texto livre pelo item do catálogo escolhido pelo coordenador. */
  public void mapCrop(UUID catalogId) {
    this.cropId = catalogId;
    this.productOtherName = null;
  }

  public UUID getCropId() {
    return cropId;
  }

  public String getProductOtherName() {
    return productOtherName;
  }

  public UUID getPlantingId() {
    return plantingId;
  }

  public QuantityValue getQuantity() {
    return quantity;
  }

  public OutflowDestination getDestination() {
    return destination;
  }

  public String getBuyerName() {
    return buyerName;
  }
}
