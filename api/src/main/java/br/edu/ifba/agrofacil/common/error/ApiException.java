// Exceção de negócio que vira Problem Details com o código do contrato.
package br.edu.ifba.agrofacil.common.error;

import java.time.Duration;
import java.util.List;

/** Lançada pelos serviços; o {@link GlobalExceptionHandler} a converte na resposta HTTP. */
public class ApiException extends RuntimeException {

  private final ErrorCode code;
  private final transient List<FieldErrorItem> fieldErrors;
  private final Duration retryAfter;

  public ApiException(ErrorCode code) {
    this(code, code.defaultMessage(), List.of(), null);
  }

  public ApiException(ErrorCode code, String message) {
    this(code, message, List.of(), null);
  }

  private ApiException(
      ErrorCode code, String message, List<FieldErrorItem> fieldErrors, Duration retryAfter) {
    super(message);
    this.code = code;
    this.fieldErrors = List.copyOf(fieldErrors);
    this.retryAfter = retryAfter;
  }

  public static ApiException notFound() {
    return new ApiException(ErrorCode.NOT_FOUND);
  }

  public static ApiException versionConflict() {
    return new ApiException(ErrorCode.VERSION_CONFLICT);
  }

  /** Erro de negócio com o código dado e a lista de campos (ex.: VISITOR_NOT_ELIGIBLE). */
  public static ApiException withErrors(
      ErrorCode code, String message, List<FieldErrorItem> errors) {
    return new ApiException(code, message, errors, null);
  }

  /** 422 VALIDATION_ERROR com a lista de campos a corrigir. */
  public static ApiException validation(List<FieldErrorItem> errors) {
    return new ApiException(
        ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), errors, null);
  }

  /** 422 VALIDATION_ERROR com um único campo. */
  public static ApiException validation(String field, String code, String message) {
    return validation(List.of(new FieldErrorItem(field, code, message)));
  }

  /** 429 com o tempo de espera para o cabeçalho {@code Retry-After}. */
  public static ApiException rateLimited(Duration retryAfter) {
    return new ApiException(
        ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.defaultMessage(), List.of(), retryAfter);
  }

  public ErrorCode code() {
    return code;
  }

  public List<FieldErrorItem> fieldErrors() {
    return fieldErrors;
  }

  /** Tempo de espera sugerido, ou {@code null}. */
  public Duration retryAfter() {
    return retryAfter;
  }
}
