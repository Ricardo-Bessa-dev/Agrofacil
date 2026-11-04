// Localização no contrato: latitude, longitude, precisão e hora da captura (doc 1 §3.2.3).
package br.edu.ifba.agrofacil.common.domain;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param lat -90..90, 6 casas
 * @param lng -180..180, 6 casas
 * @param accuracyM precisão informada pelo GPS, em metros
 * @param capturedAt quando o GPS foi lido
 */
public record GeoPoint(
    @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
    @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal lng,
    @PositiveOrZero BigDecimal accuracyM,
    Instant capturedAt) {}
