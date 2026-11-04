// Formato pedido no parâmetro "format" dos relatórios (doc 1 §3.14).
package br.edu.ifba.agrofacil.report;

import br.edu.ifba.agrofacil.common.error.ApiException;
import java.util.Locale;

public enum ReportFormat {
  PDF,
  CSV;

  /** Aceita "pdf" e "csv", sem diferenciar maiúsculas; vazio = PDF. */
  public static ReportFormat parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return PDF;
    }
    try {
      return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw ApiException.validation("format", "INVALID", "Use format=pdf ou format=csv.");
    }
  }
}
