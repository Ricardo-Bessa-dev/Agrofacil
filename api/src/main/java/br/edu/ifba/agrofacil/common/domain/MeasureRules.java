// Regras de unidade das medidas: área só aceita unidade de área, quantidade nunca (RN14, RN28).
package br.edu.ifba.agrofacil.common.domain;

import br.edu.ifba.agrofacil.common.error.FieldErrorItem;
import java.util.Optional;

public final class MeasureRules {

  public static final String UNIT_NOT_ALLOWED = "UNIT_NOT_ALLOWED";

  private MeasureRules() {}

  /** Erro em {@code <field>.unit} se a unidade da área não for hectare, m² ou tarefa. */
  public static Optional<FieldErrorItem> checkArea(String field, Area area) {
    if (area == null || UnitConversion.isAreaUnit(area.unit())) {
      return Optional.empty();
    }
    return Optional.of(
        new FieldErrorItem(
            field + ".unit", UNIT_NOT_ALLOWED, "Use hectare, metro quadrado ou tarefa."));
  }
}
