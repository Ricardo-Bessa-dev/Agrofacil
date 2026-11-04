// Acesso aos colaboradores do SPG.
package br.edu.ifba.agrofacil.spg.people;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollaboratorRepository extends JpaRepository<Collaborator, UUID> {

  Optional<Collaborator> findByIdAndAssociationId(UUID id, UUID associationId);

  List<Collaborator> findByAssociationIdOrderByName(UUID associationId);

  List<Collaborator> findByIdIn(Collection<UUID> ids);
}
