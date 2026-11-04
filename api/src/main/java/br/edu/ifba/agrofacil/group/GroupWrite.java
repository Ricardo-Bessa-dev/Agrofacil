// Dados de um grupo do SPG enviados pelo painel.
package br.edu.ifba.agrofacil.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param version obrigatória na edição; ignorada na criação
 */
public record GroupWrite(
    @NotBlank @Size(max = 80) String name,
    Integer municipalityId,
    @Size(max = 2000) String notes,
    Long version) {}
