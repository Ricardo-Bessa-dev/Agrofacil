// Área gravada no banco: o valor digitado, a unidade e o valor normalizado em m² (RN14).
package br.edu.ifba.agrofacil.common.domain;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/** Cada uso dá nome às colunas com {@code @AttributeOverride}. */
@Embeddable
public class AreaValue {

  private BigDecimal value;
  private String unit;
  private BigDecimal m2;

  protected AreaValue() {}

  public AreaValue(BigDecimal value, String unit, BigDecimal m2) {
    this.value = value;
    this.unit = unit;
    this.m2 = m2;
  }

  /** Converte do contrato, recalculando o m² com o tamanho da tarefa da associação. */
  public static AreaValue from(Area area, BigDecimal tarefaM2) {
    if (area == null) {
      return null;
    }
    BigDecimal value = Decimals.round(area.value());
    return new AreaValue(
        value, area.unit(), UnitConversion.toSquareMeters(value, area.unit(), tarefaM2));
  }

  /** Cópia independente (embeddables não devem ser compartilhados entre entidades). */
  public static AreaValue copyOf(AreaValue v) {
    return v == null ? null : new AreaValue(v.value, v.unit, v.m2);
  }

  /** Converte para o contrato; devolve {@code null} se não houver área. */
  public static Area toDto(AreaValue v) {
    return v == null || v.value == null
        ? null
        : new Area(Decimals.round(v.value), v.unit, Decimals.round(v.m2));
  }

  public BigDecimal getValue() {
    return value;
  }

  public String getUnit() {
    return unit;
  }

  public BigDecimal getM2() {
    return m2;
  }
}
