// Dados de uma área enviados pelo painel (doc 1 §3.8).
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.common.domain.Area;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * @param conversionEndsOn só com {@code IN_CONVERSION}
 * @param version obrigatória na edição
 */
public record PlotWrite(
    @NotBlank @Size(max = 60) String name,
    @Valid Area area,
    @NotNull PlotStatus status,
    LocalDate conversionEndsOn,
    @Size(max = 2000) String notes,
    Long version) {}
