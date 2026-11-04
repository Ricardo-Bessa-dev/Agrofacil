// Escreve um Problem Details direto na resposta, para os filtros que rodam fora dos controllers.
package br.edu.ifba.agrofacil.common.security;

import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.error.ProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Mesmo formato do {@code GlobalExceptionHandler}. */
@Component
public class ProblemWriter {

  private final ObjectMapper mapper;

  public ProblemWriter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code)
      throws IOException {
    response.setStatus(code.status().value());
    response.setContentType(ProblemFactory.PROBLEM_JSON.toString());
    response.setCharacterEncoding("UTF-8");
    mapper.writeValue(
        response.getOutputStream(),
        ProblemFactory.body(code, null, List.of(), request.getRequestURI()));
  }
}
