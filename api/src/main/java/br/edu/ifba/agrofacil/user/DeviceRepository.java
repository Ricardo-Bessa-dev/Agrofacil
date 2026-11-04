// Acesso aos aparelhos dos agricultores.
package br.edu.ifba.agrofacil.user;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

  List<Device> findByUserIdOrderByCreatedAtDesc(UUID userId);

  List<Device> findByUserIdAndRevokedAtIsNull(UUID userId);

  List<Device> findByUserIdInAndRevokedAtIsNull(Collection<UUID> userIds);

  /** Último contato de qualquer aparelho de cada agricultor: [userId, lastSeenAt]. */
  @Query(
      "select d.userId, max(d.lastSeenAt) from Device d where d.userId in :userIds group by d.userId")
  List<Object[]> lastSeenByUser(Collection<UUID> userIds);

  @Query("select count(d) from Device d where d.userId = :userId and d.revokedAt is null")
  long countActive(UUID userId);

  @Query("select d.lastSeenAt from Device d where d.id = :id")
  Instant lastSeen(UUID id);
}
