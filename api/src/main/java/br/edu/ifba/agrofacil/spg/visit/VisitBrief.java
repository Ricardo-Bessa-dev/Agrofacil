// Resumo da propriedade para os visitadores, montado pelo servidor (ADR-17, RN36, doc 1 §3.15).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.common.domain.Area;
import br.edu.ifba.agrofacil.common.domain.GeoPoint;
import br.edu.ifba.agrofacil.common.domain.Quantity;
import br.edu.ifba.agrofacil.property.PlotStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * O telefone do visitado só vai para os participantes daquela visita (RN40).
 *
 * @param generatedAt quando o resumo mudou pela última vez; não é gravado no JSON, para a
 *     comparação "mudou ou não" ignorar a hora
 */
public record VisitBrief(
    Instant generatedAt,
    Farmer farmer,
    PropertyInfo property,
    List<PlotInfo> plots,
    List<ActivePlanting> activePlantings,
    List<InputUse> inputsLast12Months,
    List<Purchase> purchasesLast12Months,
    List<HarvestTotal> harvestsLast12Months,
    List<OutflowTotal> outflowsLast12Months,
    long incompleteRecords,
    LocalDate lastRecordOn,
    CertificationInfo certification,
    PreviousVisit previousVisit) {

  public record Farmer(String name, String phone) {}

  public record PropertyInfo(
      String name,
      String municipalityName,
      String locality,
      String accessDirections,
      GeoPoint location,
      Area totalArea) {}

  public record PlotInfo(String name, PlotStatus status, Area area, LocalDate conversionEndsOn) {}

  public record ActivePlanting(
      String crop, String plotName, LocalDate plantedOn, LocalDate expectedHarvestOn) {}

  public record InputUse(
      String name, long timesUsed, LocalDate lastUsedOn, boolean needsAuthorization) {}

  public record Purchase(String name, String supplier, LocalDate purchasedOn) {}

  /** Totais somados por unidade. */
  public record HarvestTotal(String crop, List<Quantity> totals) {}

  public record OutflowTotal(String product, String destination, List<Quantity> totals) {}

  public record CertificationInfo(String number, LocalDate expiresOn, List<String> products) {}

  public record PreviousVisit(
      VisitType type, LocalDate doneOn, Boolean issuesFound, String recommendations) {}

  public VisitBrief withGeneratedAt(Instant when) {
    return new VisitBrief(
        when,
        farmer,
        property,
        plots,
        activePlantings,
        inputsLast12Months,
        purchasesLast12Months,
        harvestsLast12Months,
        outflowsLast12Months,
        incompleteRecords,
        lastRecordOn,
        certification,
        previousVisit);
  }
}
