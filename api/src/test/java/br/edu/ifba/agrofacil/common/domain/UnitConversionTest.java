// Testes da conversão de áreas para m² (RN14).
package br.edu.ifba.agrofacil.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class UnitConversionTest {

  private static final BigDecimal TAREFA_BAIANA = new BigDecimal("4356");

  @Test
  void hectare_squareMeter_andTarefa_convertToSquareMeters_RN14() {
    assertThat(UnitConversion.toSquareMeters(new BigDecimal("1.5"), "HECTARE", TAREFA_BAIANA))
        .isEqualByComparingTo("15000");
    assertThat(UnitConversion.toSquareMeters(new BigDecimal("250"), "SQUARE_METER", TAREFA_BAIANA))
        .isEqualByComparingTo("250");
    assertThat(UnitConversion.toSquareMeters(new BigDecimal("2"), "TAREFA", TAREFA_BAIANA))
        .isEqualByComparingTo("8712");
  }

  @Test
  void tarefa_usesTheAssociationSize_RN14() {
    assertThat(UnitConversion.toSquareMeters(BigDecimal.ONE, "TAREFA", new BigDecimal("3052")))
        .isEqualByComparingTo("3052");
  }

  @Test
  void nonAreaUnit_isRejected_RN14() {
    assertThatThrownBy(
            () -> UnitConversion.toSquareMeters(BigDecimal.ONE, "KILOGRAM", TAREFA_BAIANA))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void decimals_areRoundedToThreePlacesWithoutScientificNotation_RN28() {
    assertThat(Decimals.round(new BigDecimal("2.50049")).toPlainString()).isEqualTo("2.5");
    assertThat(Decimals.round(new BigDecimal("10.000")).toString()).isEqualTo("10");
  }
}
