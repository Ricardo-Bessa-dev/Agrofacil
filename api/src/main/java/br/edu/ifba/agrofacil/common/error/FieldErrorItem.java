// Um erro de campo do Problem Details: { field, code, message } (doc 1 §3.2.5).
package br.edu.ifba.agrofacil.common.error;

/**
 * @param field caminho do campo no JSON enviado, ex.: {@code plantedArea.value}
 * @param code código estável que o front entende, ex.: {@code DATE_IN_FUTURE}
 * @param message frase em português simples, pronta para exibir
 */
public record FieldErrorItem(String field, String code, String message) {}
