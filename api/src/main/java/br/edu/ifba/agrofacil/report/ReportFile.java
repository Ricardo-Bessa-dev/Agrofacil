// Arquivo de relatório pronto para download: nome, tipo e conteúdo.
package br.edu.ifba.agrofacil.report;

import java.text.Normalizer;
import java.util.Locale;

/**
 * @param filename nome sugerido no {@code Content-Disposition}, só com letras, números e hífens
 */
public record ReportFile(String filename, String contentType, byte[] content) {

  public static final String PDF = "application/pdf";
  public static final String CSV = "text/csv; charset=UTF-8";

  /** "Sítio Boa Vista" → "sitio-boa-vista": sem acentos nem caracteres especiais. */
  public static String slug(String text) {
    String plain =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    return plain.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }
}
