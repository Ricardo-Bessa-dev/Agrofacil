// Unidade de medida (kg, caixa, molho, tarefa…), identificada pelo código.
package br.edu.ifba.agrofacil.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "measurement_unit")
public class MeasurementUnit {

  public enum Kind {
    AREA,
    MASS,
    VOLUME,
    COUNT,
    PACKAGE
  }

  @Id private String code;
  private String nameSingular;
  private String namePlural;

  @Enumerated(EnumType.STRING)
  private Kind kind;

  /** Fator para m², kg ou L; nulo na tarefa (usa a associação) e nas embalagens. */
  private BigDecimal toBaseFactor;

  private int sortOrder;
  private boolean active;
  private Instant updatedAt;

  @Column(insertable = false, updatable = false)
  private Long syncSeq;

  protected MeasurementUnit() {}

  /**
   * Só a disponibilidade e a ordem mudam pelo painel; nome e fator ficam fixos porque os registros
   * antigos dependem deles.
   */
  public void update(boolean active, int sortOrder, Instant when) {
    this.active = active;
    this.sortOrder = sortOrder;
    this.updatedAt = when;
  }

  public String getCode() {
    return code;
  }

  public String getNameSingular() {
    return nameSingular;
  }

  public String getNamePlural() {
    return namePlural;
  }

  public Kind getKind() {
    return kind;
  }

  public BigDecimal getToBaseFactor() {
    return toBaseFactor;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
  }

  public Long getSyncSeq() {
    return syncSeq;
  }
}
