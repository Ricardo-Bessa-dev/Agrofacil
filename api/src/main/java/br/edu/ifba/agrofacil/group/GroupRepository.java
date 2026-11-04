// Acesso aos grupos do SPG.
package br.edu.ifba.agrofacil.group;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<SpgGroup, UUID> {

  Optional<SpgGroup> findByIdAndAssociationIdAndDeletedAtIsNull(UUID id, UUID associationId);

  List<SpgGroup> findByAssociationIdAndDeletedAtIsNullOrderByName(UUID associationId);

  List<SpgGroup> findByIdIn(Collection<UUID> ids);
}
