// Dá a cada requisição um id curto, que aparece em todos os logs e no cabeçalho X-Trace-Id.
package br.edu.ifba.agrofacil.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Roda antes da segurança, para que até os erros 401 tenham {@code traceId}. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Trace-Id";
  private static final int TRACE_ID_BYTES = 8; // 16 caracteres hexadecimais
  private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
  private final SecureRandom random = new SecureRandom();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    byte[] bytes = new byte[TRACE_ID_BYTES];
    random.nextBytes(bytes);
    String traceId = HexFormat.of().formatHex(bytes);
    MDC.put("traceId", traceId);
    response.setHeader(HEADER, traceId);
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      // Só método, caminho, status e tempo: nunca corpo, token ou dado pessoal (doc 1 §5.4).
      log.info(
          "{} {} -> {} em {} ms",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          (System.nanoTime() - start) / 1_000_000);
      MDC.remove("traceId");
    }
  }
}
