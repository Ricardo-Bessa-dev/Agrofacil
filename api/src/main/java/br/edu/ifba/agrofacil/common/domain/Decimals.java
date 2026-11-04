// Regras de casas decimais das quantidades e áreas (RN28).
package br.edu.ifba.agrofacil.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Decimals {

  /** Quantidades e áreas aceitam até 3 casas decimais (RN28). */
  public static final int MAX_SCALE = 3;

  private Decimals() {}

  /** Arredonda a 3 casas, como o contrato promete (doc 1 §3.2.3). */
  public static BigDecimal round(BigDecimal value) {
    if (value == null) {
      return null;
    }
    BigDecimal rounded = value.setScale(MAX_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    // stripTrailingZeros transforma 10 em 1E+1; volta para escala 0 para o JSON sair "10".
    return rounded.scale() < 0 ? rounded.setScale(0) : rounded;
  }
}
