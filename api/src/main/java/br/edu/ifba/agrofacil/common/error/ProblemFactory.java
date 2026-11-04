// Monta o corpo Problem Details (RFC 9457) no formato exato do contrato (doc 1 §3.2.5).
package br.edu.ifba.agrofacil.common.error;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

/** Usado pelo tratador global e pelos filtros de segurança, para os dois falarem igual. */
public final class ProblemFactory {

  public static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;
  private static final String TYPE_BASE = "https://agrofacil.app/errors/";

  private ProblemFactory() {}

  /** Corpo do erro, com os campos sempre presentes ({@code errors} vazio quando não há). */
  public static Map<String, Object> body(
      ErrorCode code, String detail, List<FieldErrorItem> errors, String path) {
    var body = new LinkedHashMap<String, Object>();
    body.put("type", URI.create(TYPE_BASE + code.slug()).toString());
    body.put("title", code.title());
    body.put("status", code.status().value());
    body.put("detail", detail != null ? detail : code.defaultMessage());
    body.put("instance", path);
    body.put("code", code.name());
    body.put("errors", errors != null ? errors : List.of());
    body.put("traceId", MDC.get("traceId"));
    return body;
  }
}
