// Regra RN41: a soma das áreas (talhões, canteiros) não pode passar do tamanho da propriedade.
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.common.domain.AreaValue;
import br.edu.ifba.agrofacil.common.domain.UnitConversion;
import br.edu.ifba.agrofacil.common.error.FieldErrorItem;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Funções puras: quem chama busca as áreas no banco e transforma o erro em resposta 422. */
public final class PlotAreaLimit {

  public static final String EXCEEDS_PROPERTY_AREA = "EXCEEDS_PROPERTY_AREA";
  public static final String SMALLER_THAN_PLOTS = "SMALLER_THAN_PLOTS";

  /**
   * Folga em m² para o arredondamento: cada área é convertida e arredondada separadamente, então
   * três terços de uma tarefa podem somar alguns centavos de m² a mais que a tarefa inteira.
   */
  static final BigDecimal ROUNDING_TOLERANCE_M2 = BigDecimal.ONE;

  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
  private static final int DISPLAY_DECIMALS = 2;

  private PlotAreaLimit() {}

  /** Soma em m² das áreas que têm tamanho; as sem tamanho não contam. */
  public static BigDecimal sumM2(Collection<AreaValue> areas) {
    return areas.stream()
        .filter(Objects::nonNull)
        .map(AreaValue::getM2)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /**
   * Confere se uma área cabe na propriedade, junto com as outras áreas ativas.
   *
   * @param propertyTotal tamanho da propriedade; sem tamanho, não há limite
   * @param otherPlotsM2 soma em m² das outras áreas ativas (sem a que está sendo salva)
   * @param plot área que está sendo salva; sem tamanho, sempre cabe
   * @param tarefaM2 tamanho da tarefa na associação, para escrever a mensagem
   * @return erro no campo {@code area} se passar do tamanho
   */
  public static Optional<FieldErrorItem> checkPlot(
      AreaValue propertyTotal, BigDecimal otherPlotsM2, AreaValue plot, BigDecimal tarefaM2) {
    if (!hasSize(propertyTotal) || !hasSize(plot)) {
      return Optional.empty();
    }
    if (!exceeds(otherPlotsM2.add(plot.getM2()), propertyTotal.getM2())) {
      return Optional.empty();
    }
    String unit = propertyTotal.getUnit();
    BigDecimal free = propertyTotal.getM2().subtract(otherPlotsM2).max(BigDecimal.ZERO);
    String message =
        free.signum() == 0
            ? "As outras áreas já ocupam toda a propriedade (%s). Diminua uma delas antes."
                .formatted(format(propertyTotal.getM2(), unit, tarefaM2))
            : "A propriedade tem %s e só cabem mais %s nesta área."
                .formatted(
                    format(propertyTotal.getM2(), unit, tarefaM2), format(free, unit, tarefaM2));
    return Optional.of(new FieldErrorItem("area", EXCEEDS_PROPERTY_AREA, message));
  }

  /**
   * Confere se o novo tamanho da propriedade ainda comporta as áreas cadastradas.
   *
   * @param newTotal novo tamanho; sem tamanho, não há limite
   * @param plotsM2 soma em m² das áreas ativas
   * @return erro no campo {@code totalArea} se ficar menor que as áreas
   */
  public static Optional<FieldErrorItem> checkProperty(
      AreaValue newTotal, BigDecimal plotsM2, BigDecimal tarefaM2) {
    if (!hasSize(newTotal) || !exceeds(plotsM2, newTotal.getM2())) {
      return Optional.empty();
    }
    String message =
        "As áreas cadastradas somam %s. Diminua as áreas antes de diminuir a propriedade."
            .formatted(format(plotsM2, newTotal.getUnit(), tarefaM2));
    return Optional.of(new FieldErrorItem("totalArea", SMALLER_THAN_PLOTS, message));
  }

  private static boolean hasSize(AreaValue area) {
    return area != null && area.getM2() != null;
  }

  private static boolean exceeds(BigDecimal usedM2, BigDecimal limitM2) {
    return usedM2.compareTo(limitM2.add(ROUNDING_TOLERANCE_M2)) > 0;
  }

  /** Ex.: "2,5 ha", "1.200 m²", "3 tarefas". */
  static String format(BigDecimal m2, String unit, BigDecimal tarefaM2) {
    BigDecimal value = UnitConversion.fromSquareMeters(m2, unit, tarefaM2);
    NumberFormat number = NumberFormat.getNumberInstance(PT_BR);
    number.setMaximumFractionDigits(DISPLAY_DECIMALS);
    String label =
        switch (unit) {
          case UnitConversion.HECTARE -> "ha";
          case UnitConversion.SQUARE_METER -> "m²";
          default -> value.compareTo(BigDecimal.ONE) == 0 ? "tarefa" : "tarefas";
        };
    return number.format(value) + " " + label;
  }
}
