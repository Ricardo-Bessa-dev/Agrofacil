// Acesso às notificações.
package br.edu.ifba.agrofacil.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

  Page<Notification> findByUserId(UUID userId, Pageable pageable);

  Page<Notification> findByUserIdAndReadAtIsNull(UUID userId, Pageable pageable);

  @Modifying
  @Query(
      "update Notification n set n.readAt = :when, n.updatedAt = :when, n.version = n.version + 1"
          + " where n.userId = :userId and n.readAt is null")
  int markAllRead(UUID userId, Instant when);

  @Query(
      "select n from Notification n where n.userId = :userId and n.syncSeq > :cursor order by n.syncSeq")
  List<Notification> findChanges(UUID userId, long cursor, Limit limit);
}
