// Formatos de leitura e escrita dos colaboradores (doc 1 §3.15).
package br.edu.ifba.agrofacil.spg.people;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class CollaboratorDtos {

  private CollaboratorDtos() {}

  /**
   * @param phone formato E.164, ex.: +5574999998888
   * @param groupId grupo em que participa, se houver
   */
  public record Write(
      @NotBlank @Size(max = 120) String name,
      @NotNull Collaborator.Kind kind,
      @Size(max = 120) String organization,
      @Pattern(regexp = "\\+\\d{10,15}") String phone,
      UUID groupId,
      @Size(max = 2000) String notes,
      Boolean active,
      Long version) {}

  public record View(
      UUID id,
      String name,
      Collaborator.Kind kind,
      String organization,
      String phone,
      UUID groupId,
      String notes,
      boolean active,
      Long version) {

    public static View of(Collaborator c) {
      return new View(
          c.getId(),
          c.getName(),
          c.getKind(),
          c.getOrganization(),
          c.getPhone(),
          c.getGroupId(),
          c.getNotes(),
          c.isActive(),
          c.getVersion());
    }

    public Write toWrite() {
      return new Write(name, kind, organization, phone, groupId, notes, active, version);
    }
  }
}
