// Colaborador do SPG sem login: técnico, consumidor, ONG ou órgão público (IN 19, art. 70).
package br.edu.ifba.agrofacil.spg.people;

import br.edu.ifba.agrofacil.common.persistence.VersionedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Não há exclusão: sai de uso com {@code active = false}, porque visitas antigas apontam para ele.
 */
@Entity
@Table(name = "collaborator")
public class Collaborator extends VersionedEntity {

  public enum Kind {
    TECHNICIAN,
    CONSUMER,
    ORGANIZATION,
    OTHER
  }

  private UUID associationId;
  private UUID groupId;
  private String name;

  @Enumerated(EnumType.STRING)
  private Kind kind;

  private String organization;
  private String phone;
  private String notes;
  private boolean active;

  protected Collaborator() {}

  public Collaborator(UUID associationId) {
    super(UUID.randomUUID());
    this.associationId = associationId;
  }

  public void update(CollaboratorDtos.Write w) {
    this.name = w.name().trim();
    this.kind = w.kind();
    this.organization = blankToNull(w.organization());
    this.phone = blankToNull(w.phone());
    this.groupId = w.groupId();
    this.notes = blankToNull(w.notes());
    this.active = !Boolean.FALSE.equals(w.active());
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public String getName() {
    return name;
  }

  public Kind getKind() {
    return kind;
  }

  public String getOrganization() {
    return organization;
  }

  public String getPhone() {
    return phone;
  }

  public String getNotes() {
    return notes;
  }

  public boolean isActive() {
    return active;
  }
}
