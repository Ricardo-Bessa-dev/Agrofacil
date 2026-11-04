// Quantidade gravada no banco: valor e código da unidade (sem conversão entre unidades, ADR-12).
package br.edu.ifba.agrofacil.common.domain;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

@Embeddable
public class QuantityValue {

  private BigDecimal value;
  private String unit;

  protected QuantityValue() {}

  public QuantityValue(BigDecimal value, String unit) {
    this.value = value;
    this.unit = unit;
  }

  public static QuantityValue from(Quantity q) {
    return q == null ? null : new QuantityValue(Decimals.round(q.value()), q.unit());
  }

  public static Quantity toDto(QuantityValue v) {
    return v == null || v.value == null ? null : new Quantity(Decimals.round(v.value), v.unit);
  }

  public BigDecimal getValue() {
    return value;
  }

  public String getUnit() {
    return unit;
  }
}
