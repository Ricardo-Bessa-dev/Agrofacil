// Dados de uma pessoa vinculada enviados pelo painel (doc 1 §3.8).
package br.edu.ifba.agrofacil.property;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkerWrite(
    @NotBlank @Size(max = 120) String name,
    @NotNull WorkerRelationship relationship,
    Boolean active,
    Long version) {}
