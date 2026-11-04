// Perfil do agricultor (tabela farmer), 1:1 com o AppUser: grupo, CAF, endereço e observações.
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.common.domain.AddressValue;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "farmer")
@Audited
public class FarmerProfile {

  @Id private UUID userId;
  private UUID groupId;
  private String cafNumber; // Cadastro da Agricultura Familiar (opcional)
  private Integer groupJoinedYear;
  @Embedded private AddressValue address;
  private String notes;

  protected FarmerProfile() {}

  public FarmerProfile(UUID userId) {
    this.userId = userId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public void setGroupId(UUID groupId) {
    this.groupId = groupId;
  }

  public String getCafNumber() {
    return cafNumber;
  }

  public void setCafNumber(String cafNumber) {
    this.cafNumber = cafNumber;
  }

  public Integer getGroupJoinedYear() {
    return groupJoinedYear;
  }

  public void setGroupJoinedYear(Integer groupJoinedYear) {
    this.groupJoinedYear = groupJoinedYear;
  }

  public AddressValue getAddress() {
    return address;
  }

  public void setAddress(AddressValue address) {
    this.address = address;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }
}
