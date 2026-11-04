// Monta CSV para abrir direto no Excel em português: ";" como separador, BOM UTF-8 e vírgula
// decimal.
package br.edu.ifba.agrofacil.report;

import br.edu.ifba.agrofacil.common.domain.BrFormat;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Escrito à mão (doc 2 §1.14): o formato é simples e assim não há dependência extra. */
public final class CsvWriter {

  private static final char SEPARATOR = ';';
  private static final String BOM = "﻿";
  private static final String LINE_END = "\r\n"; // o Excel espera CRLF

  private final StringBuilder out = new StringBuilder(BOM);

  /** Escreve uma linha; cada valor é convertido para o formato brasileiro. */
  public CsvWriter row(Object... values) {
    return row(Arrays.asList(values)); // aceita valores nulos (célula vazia)
  }

  public CsvWriter row(List<?> values) {
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        out.append(SEPARATOR);
      }
      out.append(escape(format(values.get(i))));
    }
    out.append(LINE_END);
    return this;
  }

  public byte[] toBytes() {
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static String format(Object value) {
    return switch (value) {
      case null -> "";
      case BigDecimal d -> BrFormat.decimal(d);
      case Double d -> BrFormat.decimal(BigDecimal.valueOf(d));
      case LocalDate d -> BrFormat.date(d);
      case Boolean b -> b ? "sim" : "não";
      case String s -> neutralizeFormula(s);
      default -> value.toString();
    };
  }

  /**
   * Texto livre que começa com "=", "+", "-" ou "@" vira fórmula no Excel (injeção de CSV). Um
   * apóstrofo na frente faz o Excel mostrar o texto como está.
   */
  private static String neutralizeFormula(String s) {
    return !s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 ? "'" + s : s;
  }

  /** Aspas em campos com ";", aspas ou quebras de linha; aspas internas são dobradas. */
  static String escape(String s) {
    boolean needsQuotes =
        s.indexOf(SEPARATOR) >= 0
            || s.indexOf('"') >= 0
            || s.indexOf('\n') >= 0
            || s.indexOf('\r') >= 0;
    return needsQuotes ? '"' + s.replace("\"", "\"\"") + '"' : s;
  }
}
