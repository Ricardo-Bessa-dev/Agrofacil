// Acesso aos insumos; a associação vê os dela e os globais (association_id nulo).
package br.edu.ifba.agrofacil.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FarmInputRepository extends JpaRepository<FarmInput, UUID> {

  @Query(
      "select i from FarmInput i where (i.associationId is null or i.associationId = :associationId)"
          + " order by i.name")
  List<FarmInput> findVisible(UUID associationId);

  @Query(
      "select i from FarmInput i where (i.associationId is null or i.associationId = :associationId)"
          + " and i.syncSeq > :cursor order by i.syncSeq")
  List<FarmInput> findChanges(UUID associationId, long cursor, Limit limit);
}
