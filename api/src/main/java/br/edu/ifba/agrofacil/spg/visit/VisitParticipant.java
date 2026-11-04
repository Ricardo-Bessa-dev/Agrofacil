// Pessoa escalada para uma visita: visitador ou observador, agricultor ou colaborador (RN32).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.common.persistence.VersionedEntity;
import br.edu.ifba.agrofacil.spg.people.PersonRef;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Quem sai da visita é excluído logicamente, para o pull mandar a lápide ao celular dele (RN36).
 */
@Entity
@Table(name = "visit_participant")
public class VisitParticipant extends VersionedEntity {

  public enum Role {
    VISITOR,
    OBSERVER
  }

  private UUID visitId;
  private UUID farmerId;
  private UUID collaboratorId;

  @Enumerated(EnumType.STRING)
  private Role role;

  private boolean suggested;

  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<String> suggestionReasons = new ArrayList<>();

  private Boolean attended;
  private Instant deletedAt;

  protected VisitParticipant() {}

  /**
   * @param id pode vir do painel, para o {@code recorderId} apontar para o participante
   */
  public VisitParticipant(UUID id, UUID visitId, PersonRef person) {
    super(id);
    this.visitId = visitId;
    this.farmerId = person.isFarmer() ? person.id() : null;
    this.collaboratorId = person.isFarmer() ? null : person.id();
  }

  public void update(Role role, boolean suggested, List<String> reasons) {
    this.role = role;
    this.suggested = suggested;
    this.suggestionReasons = new ArrayList<>(reasons);
  }

  public void setAttended(Boolean attended) {
    this.attended = attended;
  }

  public void markDeleted(Instant when) {
    this.deletedAt = when;
  }

  /** Id da pessoa (agricultor ou colaborador). */
  public UUID personId() {
    return farmerId != null ? farmerId : collaboratorId;
  }

  public boolean isFarmer() {
    return farmerId != null;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public UUID getVisitId() {
    return visitId;
  }

  public UUID getFarmerId() {
    return farmerId;
  }

  public UUID getCollaboratorId() {
    return collaboratorId;
  }

  public Role getRole() {
    return role;
  }

  public boolean isSuggested() {
    return suggested;
  }

  public List<String> getSuggestionReasons() {
    return List.copyOf(suggestionReasons);
  }

  public Boolean getAttended() {
    return attended;
  }
}
