// Acesso aos comunicados.
package br.edu.ifba.agrofacil.announcement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

  Optional<Announcement> findByIdAndAssociationIdAndDeletedAtIsNull(UUID id, UUID associationId);

  Page<Announcement> findByAssociationIdAndDeletedAtIsNull(UUID associationId, Pageable pageable);

  Page<Announcement> findByAssociationIdAndTypeAndDeletedAtIsNull(
      UUID associationId, AnnouncementType type, Pageable pageable);

  /**
   * Pull: comunicados para todos ou para o grupo do agricultor, inclusive os excluídos (lápides).
   * Sem grupo, {@code groupId} nulo e só os gerais entram.
   */
  @Query(
      "select a from Announcement a where a.associationId = :associationId and a.syncSeq > :cursor"
          + " and (a.groupId is null or a.groupId = :groupId) order by a.syncSeq")
  List<Announcement> findChanges(UUID associationId, UUID groupId, long cursor, Limit limit);
}
