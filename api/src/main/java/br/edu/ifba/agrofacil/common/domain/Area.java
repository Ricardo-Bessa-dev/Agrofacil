// Área no contrato: valor + unidade de área + m² calculado pelo servidor (doc 1 §3.2.3).
package br.edu.ifba.agrofacil.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * @param value maior que zero, até 3 casas (RN28)
 * @param unit {@code HECTARE}, {@code SQUARE_METER} ou {@code TAREFA}
 * @param m2 calculado pelo servidor (RN14); ignorado se vier do cliente
 */
public record Area(
    @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal value,
    @NotBlank @Schema(allowableValues = {"HECTARE", "SQUARE_METER", "TAREFA"}) String unit,
    @Schema(accessMode = Schema.AccessMode.READ_ONLY) BigDecimal m2) {}
