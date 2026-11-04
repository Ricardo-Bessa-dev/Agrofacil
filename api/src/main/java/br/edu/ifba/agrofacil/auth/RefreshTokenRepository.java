// Acesso aos refresh tokens.
package br.edu.ifba.agrofacil.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  List<RefreshToken> findByFamilyId(UUID familyId);

  @Query("select min(t.createdAt) from RefreshToken t where t.familyId = :familyId")
  Instant familyStartedAt(UUID familyId);

  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :when where t.familyId = :familyId and t.revokedAt is null")
  int revokeFamily(UUID familyId, Instant when);

  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :when where t.userId = :userId and t.revokedAt is null")
  int revokeAllForUser(UUID userId, Instant when);

  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :when where t.deviceId = :deviceId and t.revokedAt is null")
  int revokeAllForDevice(UUID deviceId, Instant when);
}
