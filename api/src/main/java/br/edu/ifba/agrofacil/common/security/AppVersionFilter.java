// Responde 426 APP_UPDATE_REQUIRED quando o app está abaixo da versão mínima (doc 2 §1.7.7).
package br.edu.ifba.agrofacil.common.security;

import br.edu.ifba.agrofacil.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Requisições sem o cabeçalho {@code X-App-Version} (o painel web) passam direto. */
public class AppVersionFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-App-Version";
  private final String minVersion;
  private final ProblemWriter problemWriter;

  public AppVersionFilter(String minVersion, ProblemWriter problemWriter) {
    this.minVersion = minVersion;
    this.problemWriter = problemWriter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String version = request.getHeader(HEADER);
    if (version != null && compareVersions(version, minVersion) < 0) {
      problemWriter.write(request, response, ErrorCode.APP_UPDATE_REQUIRED);
      return;
    }
    chain.doFilter(request, response);
  }

  /**
   * Compara versões no formato {@code 1.4.0}. Partes que não são número contam como 0.
   *
   * @return negativo se {@code a < b}, zero se iguais, positivo se {@code a > b}
   */
  public static int compareVersions(String a, String b) {
    String[] pa = a.trim().split("\\.");
    String[] pb = b.trim().split("\\.");
    for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
      int diff = Integer.compare(part(pa, i), part(pb, i));
      if (diff != 0) {
        return diff;
      }
    }
    return 0;
  }

  private static int part(String[] parts, int index) {
    if (index >= parts.length) {
      return 0;
    }
    try {
      return Integer.parseInt(parts[index].replaceAll("\\D.*$", ""));
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
