// Localização gravada no banco (colunas latitude, longitude e location_accuracy_m).
package br.edu.ifba.agrofacil.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** A hora da captura, quando existe, fica numa coluna da própria entidade. */
@Embeddable
public class GeoPointValue {

  private static final int COORDINATE_SCALE = 6; // numeric(9,6)
  private static final int ACCURACY_SCALE = 1; // numeric(8,1)

  private BigDecimal latitude;
  private BigDecimal longitude;

  @Column(name = "location_accuracy_m")
  private BigDecimal locationAccuracyM;

  protected GeoPointValue() {}

  public GeoPointValue(BigDecimal latitude, BigDecimal longitude, BigDecimal locationAccuracyM) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.locationAccuracyM = locationAccuracyM;
  }

  public static GeoPointValue from(GeoPoint p) {
    if (p == null) {
      return null;
    }
    return new GeoPointValue(
        p.lat().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
        p.lng().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
        p.accuracyM() == null
            ? null
            : p.accuracyM().setScale(ACCURACY_SCALE, RoundingMode.HALF_UP));
  }

  public static GeoPoint toDto(GeoPointValue v, Instant capturedAt) {
    return v == null || v.latitude == null
        ? null
        : new GeoPoint(v.latitude, v.longitude, v.locationAccuracyM, capturedAt);
  }

  public BigDecimal getLatitude() {
    return latitude;
  }

  public BigDecimal getLongitude() {
    return longitude;
  }
}
