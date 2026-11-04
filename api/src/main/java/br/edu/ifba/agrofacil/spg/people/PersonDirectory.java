// Resolve nomes de agricultores e colaboradores em lote, para montar PersonRef sem consulta por
// linha.
package br.edu.ifba.agrofacil.spg.people;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.security.Role;
import br.edu.ifba.agrofacil.user.AppUser;
import br.edu.ifba.agrofacil.user.UserRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PersonDirectory {

  private final UserRepository users;
  private final CollaboratorRepository collaborators;

  public PersonDirectory(UserRepository users, CollaboratorRepository collaborators) {
    this.users = users;
    this.collaborators = collaborators;
  }

  /** Nomes por id (agricultores e colaboradores juntos; os ids são UUIDs, sem colisão). */
  @Transactional(readOnly = true)
  public Map<UUID, String> names(Collection<UUID> farmerIds, Collection<UUID> collaboratorIds) {
    var result = new HashMap<UUID, String>();
    if (!farmerIds.isEmpty()) {
      users.findByIdIn(farmerIds).forEach(u -> result.put(u.getId(), u.getName()));
    }
    if (!collaboratorIds.isEmpty()) {
      collaborators.findByIdIn(collaboratorIds).forEach(c -> result.put(c.getId(), c.getName()));
    }
    return result;
  }

  /**
   * Confere que a pessoa existe na associação e devolve a referência com o nome.
   *
   * @param field campo do erro de validação, ex.: {@code participants[0].person}
   */
  @Transactional(readOnly = true)
  public PersonRef resolve(UUID associationId, PersonRef ref, String field) {
    String name =
        ref.isFarmer()
            ? users
                .findByIdAndAssociationIdAndRoleAndDeletedAtIsNull(
                    ref.id(), associationId, Role.FARMER)
                .map(AppUser::getName)
                .orElse(null)
            : collaborators
                .findByIdAndAssociationId(ref.id(), associationId)
                .map(Collaborator::getName)
                .orElse(null);
    if (name == null) {
      throw ApiException.validation(field, "NOT_FOUND", "Pessoa não encontrada.");
    }
    return new PersonRef(ref.kind(), ref.id(), name);
  }
}
