// Formatos brasileiros usados em textos de notificação, e-mails e relatórios.
package br.edu.ifba.agrofacil.common.domain;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public final class BrFormat {

  public static final Locale PT_BR = Locale.of("pt", "BR");

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM");

  /** Até 3 casas decimais (RN28), sem zeros à direita e sem separador de milhar. */
  private static final String DECIMAL_PATTERN = "0.###";

  private BrFormat() {}

  /** Ex.: 23/11/2026; vazio se a data for nula. */
  public static String date(LocalDate date) {
    return date == null ? "" : DATE.format(date);
  }

  /** Ex.: 1234,5; vazio se o número for nulo. */
  public static String decimal(BigDecimal value) {
    if (value == null) {
      return "";
    }
    // DecimalFormat não é thread-safe: cria um por chamada (o volume é pequeno).
    return new DecimalFormat(DECIMAL_PATTERN, DecimalFormatSymbols.getInstance(PT_BR))
        .format(value);
  }

  /** Ex.: "quinta, 14/11", como nos avisos de visita (doc 1 §6.3). */
  public static String weekdayAndDay(LocalDate date) {
    String weekday =
        date.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR).replace("-feira", "");
    return weekday + ", " + SHORT_DATE.format(date);
  }

  /** "1 dia", "2 dias". */
  public static String days(long n) {
    return n == 1 ? "1 dia" : n + " dias";
  }
}
