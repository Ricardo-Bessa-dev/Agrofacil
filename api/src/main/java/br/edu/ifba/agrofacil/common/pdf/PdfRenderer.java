// Gera PDFs a partir de templates Thymeleaf (XHTML) com openhtmltopdf e a fonte Noto Sans embutida.
package br.edu.ifba.agrofacil.common.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Usado pelos relatórios (doc 2 §1.14) e pelos documentos do SPG (§1.15.6). Os templates precisam
 * ser XHTML bem formado (tags fechadas), porque o openhtmltopdf lê como XML. Os campos vêm de
 * usuários: use sempre {@code th:text}, nunca {@code th:utext}.
 */
@Component
public class PdfRenderer {

  /** Nome da família usada no CSS dos templates: {@code font-family: 'Noto Sans'}. */
  public static final String FONT_FAMILY = "Noto Sans";

  private static final int REGULAR = 400;
  private static final int BOLD = 700;
  private static final String PRODUCER = "Agrofácil";

  private final TemplateEngine templates;

  public PdfRenderer(TemplateEngine templates) {
    this.templates = templates;
  }

  /**
   * @param template caminho dentro de {@code templates/}, sem ".html", ex.: {@code
   *     report/field-notebook}
   * @param model variáveis do template
   * @return o PDF pronto
   */
  public byte[] render(String template, Map<String, Object> model) {
    var context = new Context(java.util.Locale.of("pt", "BR"));
    context.setVariables(model);
    String html = templates.process(template, context);
    var out = new ByteArrayOutputStream();
    try {
      var builder = new PdfRendererBuilder();
      builder.useFastMode();
      builder.withProducer(PRODUCER);
      builder.useFont(
          () -> open("fonts/NotoSans-Regular.ttf"), FONT_FAMILY, REGULAR, FontStyle.NORMAL, true);
      builder.useFont(
          () -> open("fonts/NotoSans-Bold.ttf"), FONT_FAMILY, BOLD, FontStyle.NORMAL, true);
      builder.withHtmlContent(html, null);
      builder.toStream(out);
      builder.run();
    } catch (IOException e) {
      throw new UncheckedIOException("Falha ao gerar o PDF " + template, e);
    }
    return out.toByteArray();
  }

  private static java.io.InputStream open(String path) {
    try {
      return new ClassPathResource(path).getInputStream();
    } catch (IOException e) {
      throw new UncheckedIOException("Fonte não encontrada: " + path, e);
    }
  }
}
