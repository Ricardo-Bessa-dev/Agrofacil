// Acesso aos membros da Comissão de Avaliação.
package br.edu.ifba.agrofacil.spg.people;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommitteeMemberRepository extends JpaRepository<CommitteeMember, UUID> {

  Optional<CommitteeMember> findByIdAndAssociationId(UUID id, UUID associationId);

  List<CommitteeMember> findByAssociationIdOrderByStartedOnDesc(UUID associationId);
}
