// Acesso aos impedimentos entre agricultores.
package br.edu.ifba.agrofacil.spg.people;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberConflictRepository extends JpaRepository<MemberConflict, UUID> {

  Optional<MemberConflict> findByIdAndAssociationId(UUID id, UUID associationId);

  List<MemberConflict> findByAssociationIdOrderByCreatedAtDesc(UUID associationId);

  boolean existsByFarmerAIdAndFarmerBId(UUID farmerAId, UUID farmerBId);

  /** Impedimentos em que o agricultor é um dos lados. */
  @Query("select m from MemberConflict m where m.farmerAId = :farmerId or m.farmerBId = :farmerId")
  List<MemberConflict> findInvolving(UUID farmerId);
}
