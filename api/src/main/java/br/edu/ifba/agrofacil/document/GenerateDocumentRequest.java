// Pedido de geração de documento: o tipo e o assunto (doc 1 §3.16).
package br.edu.ifba.agrofacil.document;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Formato plano: cada tipo usa só alguns campos (ver doc 1 §3.16). Os campos que faltam para o tipo
 * pedido voltam como erro de validação.
 */
public record GenerateDocumentRequest(
    @NotNull DocumentKind kind,
    UUID farmerId,
    UUID propertyId,
    UUID groupId,
    @Size(max = 100) List<UUID> propertyIds,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
    UUID visitId,
    UUID certificationId) {}
