// Distância em linha reta entre dois pontos (fórmula de Haversine), sem serviço de rotas.
package br.edu.ifba.agrofacil.spg.visit;

/** Suficiente para ordenar candidatos a visitador (doc 1 §6.4); não serve para navegação. */
public final class GeoMath {

  private static final double EARTH_RADIUS_KM = 6371;

  private GeoMath() {}

  /** Distância em km entre (lat1, lng1) e (lat2, lng2), em graus decimais. */
  public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2)
                * Math.sin(dLng / 2);
    return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
  }
}
