// Traduz as violações do Bean Validation para a lista errors[] do contrato.
package br.edu.ifba.agrofacil.common.error;

import jakarta.validation.ConstraintViolation;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.validation.BindingResult;

/** Cada anotação vira um código estável, que o front pode tratar sem ler a mensagem. */
public final class ValidationErrors {

  /** Nome simples da anotação → {@code errors[].code}. */
  private static final Map<String, String> CODES =
      Map.ofEntries(
          Map.entry("NotNull", "REQUIRED"),
          Map.entry("NotBlank", "REQUIRED"),
          Map.entry("NotEmpty", "REQUIRED"),
          Map.entry("Size", "INVALID_LENGTH"),
          Map.entry("Positive", "MUST_BE_POSITIVE"),
          Map.entry("PositiveOrZero", "MUST_NOT_BE_NEGATIVE"),
          Map.entry("Digits", "TOO_MANY_DECIMALS"),
          Map.entry("Min", "OUT_OF_RANGE"),
          Map.entry("Max", "OUT_OF_RANGE"),
          Map.entry("DecimalMin", "OUT_OF_RANGE"),
          Map.entry("DecimalMax", "OUT_OF_RANGE"),
          Map.entry("Pattern", "INVALID_FORMAT"),
          Map.entry("Email", "INVALID_FORMAT"),
          Map.entry("PastOrPresent", "DATE_IN_FUTURE"));

  private ValidationErrors() {}

  public static List<FieldErrorItem> fromBindingResult(BindingResult result) {
    return result.getFieldErrors().stream()
        .map(e -> new FieldErrorItem(e.getField(), codeFor(e.getCode()), e.getDefaultMessage()))
        .sorted(Comparator.comparing(FieldErrorItem::field))
        .toList();
  }

  public static List<FieldErrorItem> fromViolations(
      Collection<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
        .map(v ->
            new FieldErrorItem(
                fieldPath(v),
                codeFor(
                    v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()),
                v.getMessage()))
        .sorted(Comparator.comparing(FieldErrorItem::field))
        .toList();
  }

  private static String codeFor(String constraint) {
    return CODES.getOrDefault(constraint, "INVALID");
  }

  /** Tira o nome do método/parâmetro que o Validator põe no começo do caminho. */
  private static String fieldPath(ConstraintViolation<?> v) {
    String path = v.getPropertyPath().toString();
    return path.replaceFirst("^[a-zA-Z]+\\.arg\\d+\\.", "");
  }
}
