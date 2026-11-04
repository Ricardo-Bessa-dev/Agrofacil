// Acesso às áreas.
package br.edu.ifba.agrofacil.property;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlotRepository extends JpaRepository<Plot, UUID> {

  List<Plot> findByPropertyIdAndDeletedAtIsNullOrderByName(UUID propertyId);

  List<Plot> findByPropertyIdInAndDeletedAtIsNull(Collection<UUID> propertyIds);

  Optional<Plot> findByIdAndDeletedAtIsNull(UUID id);

  long countByPropertyIdAndDeletedAtIsNull(UUID propertyId);

  @Query(
      "select count(p) > 0 from Plot p where p.propertyId = :propertyId and p.deletedAt is null"
          + " and lower(p.name) = lower(:name) and p.id <> :exceptId")
  boolean nameInUse(UUID propertyId, String name, UUID exceptId);

  @Query(
      "select p from Plot p where p.syncSeq > :cursor and p.propertyId in"
          + " (select pr.id from Property pr where pr.farmerId = :farmerId) order by p.syncSeq")
  List<Plot> findChanges(UUID farmerId, long cursor, Limit limit);
}
