// Área no formato do contrato (doc 1 §3.8).
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.common.domain.Area;
import br.edu.ifba.agrofacil.common.domain.AreaValue;
import java.time.LocalDate;
import java.util.UUID;

public record PlotView(
    UUID id,
    UUID propertyId,
    String name,
    Area area,
    PlotStatus status,
    LocalDate conversionEndsOn,
    String notes,
    Long version) {

  public static PlotView of(Plot p) {
    return new PlotView(
        p.getId(),
        p.getPropertyId(),
        p.getName(),
        AreaValue.toDto(p.getArea()),
        p.getStatus(),
        p.getConversionEndsOn(),
        p.getNotes(),
        p.getVersion());
  }

  public PlotWrite toWrite() {
    return new PlotWrite(name, area, status, conversionEndsOn, notes, version);
  }
}
