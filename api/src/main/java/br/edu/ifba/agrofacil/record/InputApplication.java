// Aplicação de insumo ("Usei adubo ou remédio"), num plantio ou na área inteira (RF12).
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.common.domain.AreaValue;
import br.edu.ifba.agrofacil.common.domain.QuantityValue;
import br.edu.ifba.agrofacil.record.RecordEnums.ApplicationPurpose;
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
@Table(name = "input_application")
@PrimaryKeyJoinColumn(name = "record_id")
@DiscriminatorValue("INPUT_APPLICATION")
@Audited
public class InputApplication extends ManagementRecord {

  private UUID plantingId; // nulo = área inteira (ex.: preparo do solo)
  private UUID inputId;
  private String inputOtherName;

  @Enumerated(EnumType.STRING)
  private ApplicationPurpose purpose;

  @Embedded
  @AttributeOverride(name = "value", column = @Column(name = "quantity"))
  @AttributeOverride(name = "unit", column = @Column(name = "quantity_unit"))
  private QuantityValue quantity;

  @Embedded
  @AttributeOverride(name = "value", column = @Column(name = "applied_area"))
  @AttributeOverride(name = "unit", column = @Column(name = "applied_area_unit"))
  @AttributeOverride(name = "m2", column = @Column(name = "applied_area_m2"))
  private AreaValue appliedArea;

  protected InputApplication() {}

  public InputApplication(UUID id) {
    super(id);
  }

  @Override
  public RecordType type() {
    return RecordType.INPUT_APPLICATION;
  }

  @Override
  public List<String> missingRecommendedFields(boolean hasPhoto) {
    var missing = new ArrayList<String>();
    if (quantity == null) {
      missing.add("quantity");
    }
    if (purpose == null) {
      missing.add("purpose");
    }
    return missing;
  }

  public void setFields(
      UUID plantingId,
      UUID inputId,
      String inputOtherName,
      ApplicationPurpose purpose,
      QuantityValue quantity,
      AreaValue appliedArea) {
    this.plantingId = plantingId;
    this.inputId = inputId;
    this.inputOtherName = inputOtherName;
    this.purpose = purpose;
    this.quantity = quantity;
    this.appliedArea = appliedArea;
  }

  public UUID getPlantingId() {
    return plantingId;
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

  public ApplicationPurpose getPurpose() {
    return purpose;
  }

  public QuantityValue getQuantity() {
    return quantity;
  }

  public AreaValue getAppliedArea() {
    return appliedArea;
  }
}
