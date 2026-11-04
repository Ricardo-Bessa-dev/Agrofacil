// Quantidade no contrato: valor + código de unidade (doc 1 §3.2.3).
package br.edu.ifba.agrofacil.common.domain;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * @param value maior que zero, até 3 casas (RN28)
 * @param unit código de {@code MeasurementUnit}, ex.: {@code KILOGRAM}, {@code BUNCH}
 */
public record Quantity(
    @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal value,
    @NotBlank String unit) {}
