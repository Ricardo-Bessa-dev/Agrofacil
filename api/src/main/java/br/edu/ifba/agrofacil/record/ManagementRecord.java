// Superclasse dos registros de manejo (Manejo no diagrama original), com herança JOINED.
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.common.domain.GeoPointValue;
import br.edu.ifba.agrofacil.common.persistence.SyncableEntity;
import br.edu.ifba.agrofacil.record.RecordEnums.Completeness;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordSource;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.type.SqlTypes;

/**
 * A tabela-mãe guarda o que é comum; cada subtipo tem sua tabela com o mesmo id. Alterar um subtipo
 * também atualiza esta linha (pela {@code version}), e é isso que dispara o sync_seq.
 */
@Entity
@Table(name = "management_record")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "record_type")
@Audited
public abstract class ManagementRecord extends SyncableEntity {

  /** Número mostrado como "Nº 000123"; vem da sequência do banco. */
  @NotAudited
  @Generated
  @Column(insertable = false, updatable = false)
  private Long recordNumber;

  private UUID associationId;
  private UUID farmerId; // sempre o dono da propriedade
  private UUID propertyId;
  private UUID plotId;
  private LocalDate performedOn;
  private String notes;
  @Embedded private GeoPointValue location;
  private Instant locationCapturedAt;
  private UUID deviceId;

  @Enumerated(EnumType.STRING)
  private RecordSource source;

  private UUID createdBy;
  private UUID updatedBy;
  private Instant clientCreatedAt;

  @Enumerated(EnumType.STRING)
  private Completeness completeness;

  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<String> missingFields = new ArrayList<>();

  protected ManagementRecord() {}

  protected ManagementRecord(UUID id) {
    super(id);
  }

  public abstract RecordType type();

  /**
   * Nomes (do contrato) dos campos recomendados ainda vazios (doc 1 §2.5).
   *
   * @param hasPhoto o registro tem foto anexada (conta como nota fiscal na compra)
   */
  public abstract List<String> missingRecommendedFields(boolean hasPhoto);

  /** Recalcula a completude; chamado antes de salvar e quando um anexo muda (RN09). */
  public void refreshCompleteness(boolean hasPhoto) {
    missingFields = new ArrayList<>(missingRecommendedFields(hasPhoto));
    completeness = missingFields.isEmpty() ? Completeness.COMPLETE : Completeness.INCOMPLETE;
  }

  /** Quem criou o registro e de onde; só na criação. */
  public void markCreated(UUID by, RecordSource source, UUID deviceId) {
    this.createdBy = by;
    this.source = source;
    this.deviceId = deviceId;
  }

  public void markUpdated(UUID by) {
    this.updatedBy = by;
  }

  /** Dono e local do registro, já resolvidos pelo serviço (RN11). */
  public void placeAt(UUID associationId, UUID farmerId, UUID propertyId, UUID plotId) {
    this.associationId = associationId;
    this.farmerId = farmerId;
    this.propertyId = propertyId;
    this.plotId = plotId;
  }

  public void setCommon(
      LocalDate performedOn,
      String notes,
      GeoPointValue location,
      Instant locationCapturedAt,
      Instant clientCreatedAt) {
    this.performedOn = performedOn;
    this.notes = notes;
    this.location = location;
    this.locationCapturedAt = locationCapturedAt;
    if (this.clientCreatedAt == null) {
      this.clientCreatedAt = clientCreatedAt;
    }
  }

  public Long getRecordNumber() {
    return recordNumber;
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public UUID getFarmerId() {
    return farmerId;
  }

  public UUID getPropertyId() {
    return propertyId;
  }

  public UUID getPlotId() {
    return plotId;
  }

  public LocalDate getPerformedOn() {
    return performedOn;
  }

  public String getNotes() {
    return notes;
  }

  public GeoPointValue getLocation() {
    return location;
  }

  public Instant getLocationCapturedAt() {
    return locationCapturedAt;
  }

  public UUID getDeviceId() {
    return deviceId;
  }

  public RecordSource getSource() {
    return source;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public Instant getClientCreatedAt() {
    return clientCreatedAt;
  }

  public Completeness getCompleteness() {
    return completeness;
  }

  public List<String> getMissingFields() {
    return missingFields;
  }
}
