// Acesso aos trabalhadores.
package br.edu.ifba.agrofacil.property;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkerRepository extends JpaRepository<Worker, UUID> {

  List<Worker> findByPropertyIdAndDeletedAtIsNullOrderByName(UUID propertyId);

  Optional<Worker> findByIdAndDeletedAtIsNull(UUID id);

  @Query(
      "select w from Worker w where w.syncSeq > :cursor and w.propertyId in"
          + " (select pr.id from Property pr where pr.farmerId = :farmerId) order by w.syncSeq")
  List<Worker> findChanges(UUID farmerId, long cursor, Limit limit);
}
