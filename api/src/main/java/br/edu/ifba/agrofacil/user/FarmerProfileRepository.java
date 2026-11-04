// Acesso aos perfis de agricultor.
package br.edu.ifba.agrofacil.user;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FarmerProfileRepository extends JpaRepository<FarmerProfile, UUID> {

  List<FarmerProfile> findByUserIdIn(Collection<UUID> userIds);

  /** Agricultores (não excluídos) de um grupo. */
  @Query(
      "select u from AppUser u, FarmerProfile f where u.id = f.userId and f.groupId = :groupId"
          + " and u.deletedAt is null order by u.name")
  List<AppUser> findFarmersOfGroup(UUID groupId);

  /** Agricultores ativos por grupo: [groupId, quantidade] (RN01). */
  @Query(
      "select f.groupId, count(f) from FarmerProfile f, AppUser u where u.id = f.userId"
          + " and u.deletedAt is null and u.status = :status and f.groupId in :groupIds"
          + " group by f.groupId")
  List<Object[]> countByGroup(Collection<UUID> groupIds, UserStatus status);
}
