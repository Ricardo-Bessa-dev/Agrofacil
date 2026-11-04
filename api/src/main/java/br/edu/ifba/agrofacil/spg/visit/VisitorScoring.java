// Ordena os visitadores elegíveis por distância, rodízio e equilíbrio (doc 1 §6.4, RN33).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.common.domain.BrFormat;
import br.edu.ifba.agrofacil.spg.visit.VisitorModel.Candidate;
import br.edu.ifba.agrofacil.spg.visit.VisitorModel.Place;
import br.edu.ifba.agrofacil.spg.visit.VisitorModel.Target;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** Função pura. Menor pontuação = melhor; os pesos estão em "km equivalentes". */
public final class VisitorScoring {

  static final double SAME_MUNICIPALITY_KM = 15;
  static final double OTHER_MUNICIPALITY_KM = 50;
  static final double UNKNOWN_KM = 60;
  static final double VISITED_RECENTLY_PENALTY = 30; // rodízio
  static final double RECIPROCAL_PENALTY = 30; // evita a troca "eu visito você, você me visita"
  static final double PER_PLANNED_VISIT_PENALTY = 10; // equilíbrio

  private VisitorScoring() {}

  /**
   * @param distanceKm distância usada (real ou estimada)
   * @param reasons motivos prontos para exibir, ex.: "a 4,2 km"
   */
  public record Scored(
      Candidate candidate,
      boolean distanceEstimated,
      double distanceKm,
      double score,
      List<String> reasons) {}

  public static Scored score(Candidate c, Target target) {
    var reasons = new ArrayList<String>();
    var distance = nearestDistanceKm(c.places(), target.place());
    double km;
    if (distance.isPresent()) {
      km = distance.getAsDouble();
      reasons.add("a %s km".formatted(formatKm(km)));
    } else {
      km = estimateByMunicipality(c.places(), target.place());
      reasons.add("a ~%s km (sem localização)".formatted(formatKm(km)));
    }
    double score = km;
    if (c.visitedTargetInLast12Months()) {
      score += VISITED_RECENTLY_PENALTY;
      reasons.add("já visitou esta roça no último ano");
    } else {
      reasons.add("não visitou esta roça no último ano");
    }
    if (c.visitedByTargetFarmerInLast12Months()) {
      score += RECIPROCAL_PENALTY;
      reasons.add("foi visitado por este agricultor no último ano");
    }
    score += PER_PLANNED_VISIT_PENALTY * c.plannedVisitsNext30Days();
    if (c.plannedVisitsNext30Days() > 0) {
      reasons.add("%d visita(s) marcada(s)".formatted(c.plannedVisitsNext30Days()));
    }
    return new Scored(c, distance.isEmpty(), km, score, reasons);
  }

  /** Distância da propriedade do candidato mais próxima; vazio se faltar GPS. */
  static OptionalDouble nearestDistanceKm(List<Place> places, Place target) {
    if (!target.hasLocation()) {
      return OptionalDouble.empty();
    }
    return places.stream()
        .filter(Place::hasLocation)
        .mapToDouble(p -> GeoMath.haversineKm(p.lat(), p.lng(), target.lat(), target.lng()))
        .min();
  }

  /** Sem GPS: mesmo município = 15; outro = 50; desconhecido = 60. */
  static double estimateByMunicipality(List<Place> places, Place target) {
    if (target.municipalityId() == null) {
      return UNKNOWN_KM;
    }
    var known = places.stream().map(Place::municipalityId).filter(Objects::nonNull).toList();
    if (known.isEmpty()) {
      return UNKNOWN_KM;
    }
    return known.contains(target.municipalityId()) ? SAME_MUNICIPALITY_KM : OTHER_MUNICIPALITY_KM;
  }

  /** Uma casa decimal, com vírgula: 4,2. */
  static String formatKm(double km) {
    return BrFormat.decimal(BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP));
  }
}
