// Insumo do catálogo, semeado dos Anexos V e VII da Portaria 52/2021 (ADR-07).
package br.edu.ifba.agrofacil.catalog;

import br.edu.ifba.agrofacil.common.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "farm_input")
public class FarmInput extends BaseEntity {

  private UUID associationId;
  private String name;

  @Enumerated(EnumType.STRING)
  private InputType inputType;

  private String regulatoryReference; // ex.: "Portaria MAPA 52/2021, Anexo V"
  private boolean needsAuthorization; // o OAC precisa autorizar
  private boolean homeMade; // feito na propriedade
  private boolean active;

  protected FarmInput() {}

  /** Insumo criado pela associação (os globais vêm das migrações). */
  public FarmInput(UUID associationId) {
    super(UUID.randomUUID());
    this.associationId = associationId;
  }

  public void update(
      String name,
      InputType inputType,
      String regulatoryReference,
      boolean needsAuthorization,
      boolean homeMade,
      boolean active) {
    this.name = name;
    this.inputType = inputType;
    this.regulatoryReference = regulatoryReference;
    this.needsAuthorization = needsAuthorization;
    this.homeMade = homeMade;
    this.active = active;
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public String getName() {
    return name;
  }

  public InputType getInputType() {
    return inputType;
  }

  public String getRegulatoryReference() {
    return regulatoryReference;
  }

  public boolean isNeedsAuthorization() {
    return needsAuthorization;
  }

  public boolean isHomeMade() {
    return homeMade;
  }

  public boolean isActive() {
    return active;
  }
}
