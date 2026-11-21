// Testes da regra que impede as áreas de passarem do tamanho da propriedade (RN41).
package br.edu.ifba.agrofacil.property;

import static org.assertj.core.api.Assertions.assertThat;

import br.edu.ifba.agrofacil.common.domain.Area;
import br.edu.ifba.agrofacil.common.domain.AreaValue;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlotAreaLimitTest {

  private static final BigDecimal TAREFA_BAIANA = new BigDecimal("4356");

  private static AreaValue area(String value, String unit) {
    return AreaValue.from(new Area(new BigDecimal(value), unit, null), TAREFA_BAIANA);
  }

  @Test
  void plotThatFitsInTheFreeArea_isAccepted_RN41() {
    var tenHectares = area("10", "HECTARE");
    var usedM2 = new BigDecimal("60000"); // 6 ha já ocupados
    assertThat(PlotAreaLimit.checkPlot(tenHectares, usedM2, area("4", "HECTARE"), TAREFA_BAIANA))
        .isEmpty();
  }

  @Test
  void plotBiggerThanTheFreeArea_isRejected_andSaysHowMuchStillFits_RN41() {
    var error =
        PlotAreaLimit.checkPlot(
            area("10", "HECTARE"), new BigDecimal("75000"), area("3", "HECTARE"), TAREFA_BAIANA);
    assertThat(error).isPresent();
    assertThat(error.get().field()).isEqualTo("area");
    assertThat(error.get().code()).isEqualTo(PlotAreaLimit.EXCEEDS_PROPERTY_AREA);
    assertThat(error.get().message()).contains("10 ha").contains("2,5 ha");
  }

  @Test
  void whenTheOtherPlotsFillTheProperty_theMessageAsksToShrinkOne_RN41() {
    var error =
        PlotAreaLimit.checkPlot(
            area("3", "TAREFA"), new BigDecimal("13068"), area("1", "TAREFA"), TAREFA_BAIANA);
    assertThat(error.get().message()).contains("3 tarefas").contains("Diminua");
  }

  @Test
  void withoutSizeOnThePropertyOrOnThePlot_thereIsNoLimit_RN41() {
    assertThat(
            PlotAreaLimit.checkPlot(
                null, new BigDecimal("999999"), area("5", "HECTARE"), TAREFA_BAIANA))
        .isEmpty();
    assertThat(
            PlotAreaLimit.checkPlot(
                area("1", "HECTARE"), new BigDecimal("10000"), null, TAREFA_BAIANA))
        .isEmpty();
  }

  @Test
  void lessThanOneSquareMeterOver_isToleratedAsRounding_RN41() {
    var total = area("1000", "SQUARE_METER");
    var otherM2 = new BigDecimal("600");
    assertThat(
            PlotAreaLimit.checkPlot(total, otherM2, area("400.5", "SQUARE_METER"), TAREFA_BAIANA))
        .isEmpty();
    assertThat(PlotAreaLimit.checkPlot(total, otherM2, area("402", "SQUARE_METER"), TAREFA_BAIANA))
        .isPresent();
  }

  @Test
  void propertySmallerThanItsPlots_isRejected_RN41() {
    var error =
        PlotAreaLimit.checkProperty(
            area("2000", "SQUARE_METER"), new BigDecimal("2500"), TAREFA_BAIANA);
    assertThat(error).isPresent();
    assertThat(error.get().field()).isEqualTo("totalArea");
    assertThat(error.get().code()).isEqualTo(PlotAreaLimit.SMALLER_THAN_PLOTS);
    assertThat(error.get().message()).contains("2.500 m²");
    assertThat(
            PlotAreaLimit.checkProperty(
                area("3000", "SQUARE_METER"), new BigDecimal("2500"), TAREFA_BAIANA))
        .isEmpty();
  }

  @Test
  void sum_ignoresPlotsWithoutSize() {
    List<AreaValue> areas = Arrays.asList(area("1", "HECTARE"), null, area("500", "SQUARE_METER"));
    assertThat(PlotAreaLimit.sumM2(areas)).isEqualByComparingTo("10500");
  }
}
