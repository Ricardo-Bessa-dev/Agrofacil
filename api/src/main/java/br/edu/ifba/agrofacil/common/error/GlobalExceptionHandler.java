// Converte qualquer exceção em Problem Details (RFC 9457) no formato do contrato (doc 1 §3.2.5).
package br.edu.ifba.agrofacil.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Nunca devolve stack trace, SQL ou caminhos internos: só o {@code traceId} (doc 1 §5.2). */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Map<String, Object>> handleApi(ApiException ex, HttpServletRequest req) {
    var response = respond(ex.code(), ex.getMessage(), ex.fieldErrors(), req);
    if (ex.retryAfter() != null) {
      return ResponseEntity.status(ex.code().status())
          .contentType(ProblemFactory.PROBLEM_JSON)
          .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfter().toSeconds()))
          .body(response.getBody());
    }
    return response;
  }

  @ExceptionHandler(BindException.class) // inclui MethodArgumentNotValidException (@Valid)
  ResponseEntity<Map<String, Object>> handleBind(BindException ex, HttpServletRequest req) {
    return respond(
        ErrorCode.VALIDATION_ERROR,
        null,
        ValidationErrors.fromBindingResult(ex.getBindingResult()),
        req);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<Map<String, Object>> handleConstraint(
      ConstraintViolationException ex, HttpServletRequest req) {
    return respond(
        ErrorCode.VALIDATION_ERROR,
        null,
        ValidationErrors.fromViolations(ex.getConstraintViolations()),
        req);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    MissingServletRequestPartException.class,
    MissingRequestHeaderException.class,
    PropertyReferenceException.class // ordenação por campo que não existe
  })
  ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex, HttpServletRequest req) {
    log.debug("Requisição malformada: {}", ex.getMessage());
    return respond(ErrorCode.BAD_REQUEST, null, List.of(), req);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<Map<String, Object>> handleVersion(HttpServletRequest req) {
    return respond(ErrorCode.VERSION_CONFLICT, null, List.of(), req);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Map<String, Object>> handleForbidden(HttpServletRequest req) {
    return respond(ErrorCode.FORBIDDEN, null, List.of(), req);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<Map<String, Object>> handleNoResource(HttpServletRequest req) {
    return respond(ErrorCode.NOT_FOUND, null, List.of(), req);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<Map<String, Object>> handleMethod(HttpServletRequest req) {
    return respond(ErrorCode.METHOD_NOT_ALLOWED, null, List.of(), req);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<Map<String, Object>> handleMediaType(HttpServletRequest req) {
    return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, null, List.of(), req);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<Map<String, Object>> handleUploadSize(HttpServletRequest req) {
    return respond(ErrorCode.FILE_TOO_LARGE, null, List.of(), req);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest req) {
    log.error("Erro inesperado em {} {}", req.getMethod(), req.getRequestURI(), ex);
    return respond(ErrorCode.INTERNAL_ERROR, null, List.of(), req);
  }

  private static ResponseEntity<Map<String, Object>> respond(
      ErrorCode code, String detail, List<FieldErrorItem> errors, HttpServletRequest req) {
    return ResponseEntity.status(code.status())
        .contentType(ProblemFactory.PROBLEM_JSON)
        .body(ProblemFactory.body(code, detail, errors, requestPath(req)));
  }

  /** Caminho com o prefixo /api/v1, como o cliente chamou. */
  private static String requestPath(HttpServletRequest req) {
    return req.getRequestURI();
  }
}
