// Testes do formato CSV para o Excel em português e da proteção contra injeção de fórmula.
package br.edu.ifba.agrofacil.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

  private static String csv(Object... values) {
    return new String(new CsvWriter().row(values).toBytes(), StandardCharsets.UTF_8);
  }

  @Test
  void startsWithBom_usesSemicolon_commaDecimal_andBrazilianDate() {
    assertThat(csv("Umbu", new BigDecimal("1234.5"), LocalDate.of(2026, 11, 4), true))
        .isEqualTo("﻿Umbu;1234,5;04/11/2026;sim\r\n");
  }

  @Test
  void quotesFieldsWithSeparatorQuotesOrLineBreaks() {
    assertThat(csv("a;b", "diz \"oi\"", "linha\nnova"))
        .isEqualTo("﻿\"a;b\";\"diz \"\"oi\"\"\";\"linha\nnova\"\r\n");
  }

  @Test
  void textThatLooksLikeAFormula_isNeutralized() {
    assertThat(csv("=1+1", "@SUM(A1)", "-2+3")).isEqualTo("﻿'=1+1;'@SUM(A1);'-2+3\r\n");
    assertThat(csv(new BigDecimal("-2"))).isEqualTo("﻿-2\r\n"); // número negativo não muda
  }
}
