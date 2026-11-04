// Aplica um PATCH parcial sobre o estado atual: campo ausente = não muda; null = apaga (doc 1
// §3.1).
package br.edu.ifba.agrofacil.common.web;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ValidationErrors;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Junta o corpo do PATCH com a versão atual do recurso (no formato de escrita) e valida o
 * resultado. Assim o PATCH reaproveita as mesmas regras da edição completa.
 */
@Component
public class JsonPatch {

  private final ObjectMapper mapper;
  private final Validator validator;

  public JsonPatch(ObjectMapper mapper, Validator validator) {
    this.mapper = mapper;
    this.validator = validator;
  }

  /**
   * @param current estado atual no formato de escrita
   * @param patch corpo recebido (objeto JSON)
   * @param type classe do formato de escrita
   * @return o novo estado completo, já validado
   */
  public <T> T merge(T current, JsonNode patch, Class<T> type) {
    if (patch == null || !patch.isObject()) {
      throw ApiException.validation("", "INVALID_FORMAT", "O corpo precisa ser um objeto JSON.");
    }
    ObjectNode base = mapper.valueToTree(current);
    mergeInto(base, (ObjectNode) patch);
    T merged = mapper.treeToValue(base, type);
    var violations = validator.validate(merged);
    if (!violations.isEmpty()) {
      throw ApiException.validation(ValidationErrors.fromViolations(violations));
    }
    return merged;
  }

  /** Converte e valida um corpo genérico (usado pela sincronização, onde o @Valid não chega). */
  public <T> T convertAndValidate(JsonNode node, Class<T> type) {
    T value;
    try {
      value = mapper.treeToValue(node, type);
    } catch (JacksonException e) {
      // Erro de formato é definitivo: o app não deve repetir a mutação para sempre.
      throw ApiException.validation(
          "data", "INVALID_FORMAT", "Os dados enviados estão em formato inválido.");
    }
    var violations = validator.validate(value);
    if (!violations.isEmpty()) {
      throw ApiException.validation(ValidationErrors.fromViolations(violations));
    }
    return value;
  }

  /** Objetos aninhados (ex.: {@code address}) também são mesclados campo a campo. */
  private static void mergeInto(ObjectNode target, ObjectNode patch) {
    for (var entry : patch.properties()) {
      JsonNode value = entry.getValue();
      JsonNode existing = target.get(entry.getKey());
      if (value.isObject() && existing != null && existing.isObject()) {
        mergeInto((ObjectNode) existing, (ObjectNode) value);
      } else {
        target.set(entry.getKey(), value);
      }
    }
  }
}
