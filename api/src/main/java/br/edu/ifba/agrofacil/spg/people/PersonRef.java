// Referência a uma pessoa do SPG, que pode ser agricultor (com login) ou colaborador (sem login).
package br.edu.ifba.agrofacil.spg.people;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * @param name preenchido pelo servidor; ignorado se vier do cliente
 */
public record PersonRef(@NotNull Kind kind, @NotNull UUID id, String name) {

  public enum Kind {
    FARMER,
    COLLABORATOR
  }

  public static PersonRef farmer(UUID id, String name) {
    return new PersonRef(Kind.FARMER, id, name);
  }

  public static PersonRef collaborator(UUID id, String name) {
    return new PersonRef(Kind.COLLABORATOR, id, name);
  }

  public boolean isFarmer() {
    return kind == Kind.FARMER;
  }
}
