// Propriedade (unidade de produção) de um agricultor; é ela que recebe a certificação.
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.common.domain.AddressValue;
import br.edu.ifba.agrofacil.common.domain.AreaValue;
import br.edu.ifba.agrofacil.common.domain.GeoPointValue;
import br.edu.ifba.agrofacil.common.persistence.SyncableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "property")
@Audited
public class Property extends SyncableEntity {

  private UUID associationId;
  private UUID farmerId;
  private String name;

  @Embedded
  @AttributeOverride(name = "value", column = @Column(name = "total_area"))
  @AttributeOverride(name = "unit", column = @Column(name = "total_area_unit"))
  @AttributeOverride(name = "m2", column = @Column(name = "total_area_m2"))
  private AreaValue totalArea;

  private String productionDescription; // ex.: "Hortaliças e frutas"
  @Embedded private GeoPointValue location;
  @Embedded private AddressValue address;
  private String accessDirections; // roteiro de acesso
  private UUID createdBy;

  protected Property() {}

  /**
   * @param id gerado no app quando o agricultor cria offline; senão, pelo servidor
   */
  public Property(UUID id, UUID associationId, UUID farmerId, UUID createdBy) {
    super(id);
    this.associationId = associationId;
    this.farmerId = farmerId;
    this.createdBy = createdBy;
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public UUID getFarmerId() {
    return farmerId;
  }

  public void setFarmerId(UUID farmerId) {
    this.farmerId = farmerId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public AreaValue getTotalArea() {
    return totalArea;
  }

  public void setTotalArea(AreaValue totalArea) {
    this.totalArea = totalArea;
  }

  public String getProductionDescription() {
    return productionDescription;
  }

  public void setProductionDescription(String productionDescription) {
    this.productionDescription = productionDescription;
  }

  public GeoPointValue getLocation() {
    return location;
  }

  public void setLocation(GeoPointValue location) {
    this.location = location;
  }

  public AddressValue getAddress() {
    return address;
  }

  public void setAddress(AddressValue address) {
    this.address = address;
  }

  public String getAccessDirections() {
    return accessDirections;
  }

  public void setAccessDirections(String accessDirections) {
    this.accessDirections = accessDirections;
  }
}
