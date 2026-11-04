// Membro da Comissão de Avaliação: um titular e um suplente por grupo (doc 1 §6.1).
package br.edu.ifba.agrofacil.spg.people;

import br.edu.ifba.agrofacil.common.persistence.VersionedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** A pessoa é um agricultor ou um colaborador, nunca os dois. */
@Entity
@Table(name = "committee_member")
public class CommitteeMember extends VersionedEntity {

  public enum Role {
    HOLDER,
    ALTERNATE
  }

  private UUID associationId;
  private UUID farmerId;
  private UUID collaboratorId;
  private UUID representedGroupId;

  @Enumerated(EnumType.STRING)
  private Role role;

  private LocalDate startedOn;
  private LocalDate endedOn;

  protected CommitteeMember() {}

  public CommitteeMember(UUID associationId, UUID farmerId, UUID collaboratorId) {
    super(UUID.randomUUID());
    this.associationId = associationId;
    this.farmerId = farmerId;
    this.collaboratorId = collaboratorId;
  }

  public void update(UUID representedGroupId, Role role, LocalDate startedOn, LocalDate endedOn) {
    this.representedGroupId = representedGroupId;
    this.role = role;
    this.startedOn = startedOn;
    this.endedOn = endedOn;
  }

  /** Participa da Comissão na data (começou e ainda não terminou). */
  public boolean isActiveOn(LocalDate day) {
    return !startedOn.isAfter(day) && (endedOn == null || endedOn.isAfter(day));
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public UUID getFarmerId() {
    return farmerId;
  }

  public UUID getCollaboratorId() {
    return collaboratorId;
  }

  public UUID getRepresentedGroupId() {
    return representedGroupId;
  }

  public Role getRole() {
    return role;
  }

  public LocalDate getStartedOn() {
    return startedOn;
  }

  public LocalDate getEndedOn() {
    return endedOn;
  }
}
