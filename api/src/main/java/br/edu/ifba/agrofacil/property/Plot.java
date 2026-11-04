// Área (lote, talhão, canteiro, roça) de uma propriedade, com status de conversão (ADR-05).
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.common.domain.AreaValue;
import br.edu.ifba.agrofacil.common.persistence.SyncableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "plot")
@Audited
public class Plot extends SyncableEntity {

  /** Nome da área criada junto com a propriedade (RN06). */
  public static final String MAIN_PLOT_NAME = "Área principal";

  private UUID propertyId;
  private String name;

  @Embedded
  @AttributeOverride(name = "value", column = @Column(name = "area"))
  @AttributeOverride(name = "unit", column = @Column(name = "area_unit"))
  @AttributeOverride(name = "m2", column = @Column(name = "area_m2"))
  private AreaValue area;

  @Enumerated(EnumType.STRING)
  private PlotStatus status;

  private LocalDate conversionEndsOn; // só com IN_CONVERSION
  private String notes;

  protected Plot() {}

  public Plot(UUID propertyId, String name, AreaValue area, PlotStatus status) {
    super(UUID.randomUUID());
    this.propertyId = propertyId;
    this.name = name;
    this.area = area;
    this.status = status;
  }

  public void update(
      String name, AreaValue area, PlotStatus status, LocalDate conversionEndsOn, String notes) {
    this.name = name;
    this.area = area;
    this.status = status;
    this.conversionEndsOn = conversionEndsOn;
    this.notes = notes;
  }

  public UUID getPropertyId() {
    return propertyId;
  }

  public String getName() {
    return name;
  }

  public AreaValue getArea() {
    return area;
  }

  public PlotStatus getStatus() {
    return status;
  }

  public LocalDate getConversionEndsOn() {
    return conversionEndsOn;
  }

  public String getNotes() {
    return notes;
  }
}
