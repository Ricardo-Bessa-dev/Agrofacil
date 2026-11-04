// Grupo do SPG: pelo menos 3 agricultores que fazem as visitas de pares (RN01).
package br.edu.ifba.agrofacil.group;

import br.edu.ifba.agrofacil.common.persistence.SyncableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "spg_group")
@Audited
public class SpgGroup extends SyncableEntity {

  /** Grupo com menos agricultores gera aviso, não erro (RN01). */
  public static final int MIN_FARMERS = 3;

  private UUID associationId;
  private String name;
  private Integer municipalityId;
  private String notes;

  protected SpgGroup() {}

  public SpgGroup(UUID associationId) {
    super(UUID.randomUUID());
    this.associationId = associationId;
  }

  public void update(GroupWrite w) {
    name = w.name().trim();
    municipalityId = w.municipalityId();
    notes = w.notes();
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public String getName() {
    return name;
  }

  public Integer getMunicipalityId() {
    return municipalityId;
  }

  public String getNotes() {
    return notes;
  }
}
