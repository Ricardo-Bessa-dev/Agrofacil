// Acesso às culturas; a associação vê as dela e as globais (association_id nulo).
package br.edu.ifba.agrofacil.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CropRepository extends JpaRepository<Crop, UUID> {

  @Query(
      "select c from Crop c where (c.associationId is null or c.associationId = :associationId)"
          + " order by c.name")
  List<Crop> findVisible(UUID associationId);

  @Query(
      "select c from Crop c where (c.associationId is null or c.associationId = :associationId)"
          + " and c.syncSeq > :cursor order by c.syncSeq")
  List<Crop> findChanges(UUID associationId, long cursor, Limit limit);
}
