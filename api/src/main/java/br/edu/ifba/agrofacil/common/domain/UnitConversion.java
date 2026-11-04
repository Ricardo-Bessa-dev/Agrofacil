// Converte áreas para m² (RN14): hectare = 10.000, tarefa = tamanho da associação, m² = 1.
package br.edu.ifba.agrofacil.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/** Função pura; a conversão é sempre refeita no servidor e nunca vem do cliente. */
public final class UnitConversion {

  public static final String HECTARE = "HECTARE";
  public static final String SQUARE_METER = "SQUARE_METER";
  public static final String TAREFA = "TAREFA";
  public static final Set<String> AREA_UNITS = Set.of(HECTARE, SQUARE_METER, TAREFA);

  private static final BigDecimal M2_PER_HECTARE = BigDecimal.valueOf(10_000);
  private static final int M2_SCALE = 2; // coluna numeric(14,2)

  private UnitConversion() {}

  public static boolean isAreaUnit(String unit) {
    return unit != null && AREA_UNITS.contains(unit);
  }

  /**
   * @param value área digitada (maior que zero)
   * @param unit {@code HECTARE}, {@code SQUARE_METER} ou {@code TAREFA}
   * @param tarefaM2 tamanho da tarefa na associação, em m² (padrão 4.356)
   * @return a área em m², com 2 casas
   * @throws IllegalArgumentException se a unidade não for de área
   */
  public static BigDecimal toSquareMeters(BigDecimal value, String unit, BigDecimal tarefaM2) {
    BigDecimal factor =
        switch (unit) {
          case HECTARE -> M2_PER_HECTARE;
          case SQUARE_METER -> BigDecimal.ONE;
          case TAREFA -> tarefaM2;
          case null, default -> throw new IllegalArgumentException("Unidade de área inválida");
        };
    return value.multiply(factor).setScale(M2_SCALE, RoundingMode.HALF_UP);
  }
}
