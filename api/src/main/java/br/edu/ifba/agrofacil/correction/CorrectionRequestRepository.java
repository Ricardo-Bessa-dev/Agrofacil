// Acesso aos pedidos de correção.
package br.edu.ifba.agrofacil.correction;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CorrectionRequestRepository extends JpaRepository<CorrectionRequest, UUID> {

  List<CorrectionRequest> findByRecordIdOrderByCreatedAtDesc(UUID recordId);

  List<CorrectionRequest> findByRecordIdAndStatus(UUID recordId, CorrectionRequest.Status status);

  /** Pedidos abertos por registro: [recordId, quantidade]. */
  @Query(
      "select c.recordId, count(c) from CorrectionRequest c where c.status = 'OPEN'"
          + " and c.recordId in :recordIds group by c.recordId")
  List<Object[]> countOpenByRecord(Collection<UUID> recordIds);

  @Query(
      "select c from CorrectionRequest c where c.status = coalesce(:status, c.status) and c.recordId in"
          + " (select r.id from ManagementRecord r where r.associationId = :associationId"
          + " and (:farmerId is null or r.farmerId = :farmerId))")
  Page<CorrectionRequest> search(
      UUID associationId, CorrectionRequest.Status status, UUID farmerId, Pageable pageable);

  @Query(
      "select c from CorrectionRequest c where c.syncSeq > :cursor and c.recordId in"
          + " (select r.id from ManagementRecord r where r.farmerId = :farmerId) order by c.syncSeq")
  List<CorrectionRequest> findChanges(UUID farmerId, long cursor, Limit limit);
}
