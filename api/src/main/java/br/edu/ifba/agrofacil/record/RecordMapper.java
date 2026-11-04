// Converte entidades de registro para o formato do contrato.
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.common.domain.AreaValue;
import br.edu.ifba.agrofacil.common.domain.GeoPointValue;
import br.edu.ifba.agrofacil.common.domain.QuantityValue;
import java.util.List;

/** Função pura: o número de pedidos de correção abertos vem de fora. */
public final class RecordMapper {

  private RecordMapper() {}

  public static RecordView toView(ManagementRecord r, long openCorrectionRequests) {
    var common =
        new RecordView.Common(
            r.getId(),
            r.type(),
            r.getRecordNumber(),
            r.getFarmerId(),
            r.getPropertyId(),
            r.getPlotId(),
            r.getPerformedOn(),
            r.getNotes(),
            GeoPointValue.toDto(r.getLocation(), r.getLocationCapturedAt()),
            r.getClientCreatedAt(),
            r.getDeviceId(),
            r.getSource(),
            r.getCreatedBy(),
            r.getUpdatedBy(),
            r.getCompleteness(),
            List.copyOf(r.getMissingFields()),
            openCorrectionRequests,
            r.getCreatedAt(),
            r.getUpdatedAt(),
            r.getVersion());
    return switch (r) {
      case Planting p ->
          new RecordView.PlantingView(
              common,
              p.getCropId(),
              p.getCropOtherName(),
              p.getVariety(),
              AreaValue.toDto(p.getPlantedArea()),
              p.getPropagationType(),
              QuantityValue.toDto(p.getPropagationQuantity()),
              p.getSeedOrigin(),
              p.getSeedSupplier(),
              p.getExpectedHarvestOn(),
              p.getCycleStatus(),
              p.getFinishedOn());
      case InputApplication a ->
          new RecordView.InputApplicationView(
              common,
              a.getPlantingId(),
              a.getInputId(),
              a.getInputOtherName(),
              a.getPurpose(),
              QuantityValue.toDto(a.getQuantity()),
              AreaValue.toDto(a.getAppliedArea()));
      case Harvest h ->
          new RecordView.HarvestView(
              common,
              h.getPlantingId(),
              QuantityValue.toDto(h.getQuantity()),
              AreaValue.toDto(h.getHarvestedArea()),
              h.isFinalHarvest());
      case InputPurchase ip ->
          new RecordView.InputPurchaseView(
              common,
              ip.getInputId(),
              ip.getInputOtherName(),
              QuantityValue.toDto(ip.getQuantity()),
              ip.getSupplier());
      case Outflow o ->
          new RecordView.OutflowView(
              common,
              o.getCropId(),
              o.getProductOtherName(),
              o.getPlantingId(),
              QuantityValue.toDto(o.getQuantity()),
              o.getDestination(),
              o.getBuyerName());
      default -> throw new IllegalStateException("Tipo de registro desconhecido: " + r.type());
    };
  }
}
