// Compra de insumo, exigida pela Portaria 52, art. 11; pertence à propriedade (RN27).
package br.edu.ifba.agrofacil.record;

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
@Table(name = "input_purchase")
@PrimaryKeyJoinColumn(name = "record_id")
@DiscriminatorValue("INPUT_PURCHASE")
@Audited
public class InputPurchase extends ManagementRecord {

  /** Nome do campo que falta quando não há fornecedor nem foto da nota (doc 1 §2.5). */
  public static final String SUPPLIER_OR_RECEIPT = "supplierOrReceipt";

  private UUID inputId;
  private String inputOtherName;

  @Embedded
  @AttributeOverride(name = "value", column = @Column(name = "quantity"))
  @AttributeOverride(name = "unit", column = @Column(name = "quantity_unit"))
  private QuantityValue quantity;

  private String supplier;

  protected InputPurchase() {}

  public InputPurchase(UUID id) {
    super(id);
  }

  @Override
  public RecordType type() {
    return RecordType.INPUT_PURCHASE;
  }

  @Override
  public List<String> missingRecommendedFields(boolean hasPhoto) {
    var missing = new ArrayList<String>();
    if (quantity == null) {
      missing.add("quantity");
    }
    if ((supplier == null || supplier.isBlank()) && !hasPhoto) {
      missing.add(SUPPLIER_OR_RECEIPT);
    }
    return missing;
  }

  public void setFields(
      UUID inputId, String inputOtherName, QuantityValue quantity, String supplier) {
    this.inputId = inputId;
    this.inputOtherName = inputOtherName;
    this.quantity = quantity;
    this.supplier = supplier;
  }

  /** RN22: troca o texto livre pelo item do catálogo escolhido pelo coordenador. */
  public void mapInput(UUID catalogId) {
    this.inputId = catalogId;
    this.inputOtherName = null;
  }

  public UUID getInputId() {
    return inputId;
  }

  public String getInputOtherName() {
    return inputOtherName;
  }

  public QuantityValue getQuantity() {
    return quantity;
  }

  public String getSupplier() {
    return supplier;
  }
}
