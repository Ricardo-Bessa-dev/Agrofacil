// Motivo exigido para suspender ou cancelar uma certificação (RN19).
package br.edu.ifba.agrofacil.certification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StatusReason(@NotBlank @Size(max = 500) String reason) {}
