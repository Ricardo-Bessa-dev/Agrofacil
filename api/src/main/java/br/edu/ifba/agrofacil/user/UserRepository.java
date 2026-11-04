// Acesso aos usuários; toda busca de tela filtra a associação e ignora os excluídos.
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.common.security.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository
    extends JpaRepository<AppUser, UUID>, JpaSpecificationExecutor<AppUser> {

  Optional<AppUser> findByIdAndAssociationIdAndDeletedAtIsNull(UUID id, UUID associationId);

  Optional<AppUser> findByIdAndAssociationIdAndRoleAndDeletedAtIsNull(
      UUID id, UUID associationId, Role role);

  Optional<AppUser> findByCpfAndRoleAndDeletedAtIsNull(String cpf, Role role);

  @Query("select u from AppUser u where lower(u.email) = lower(:email) and u.deletedAt is null")
  Optional<AppUser> findByEmailIgnoreCase(String email);

  boolean existsByCpfAndDeletedAtIsNull(String cpf);

  @Query(
      "select count(u) > 0 from AppUser u where lower(u.email) = lower(:email)"
          + " and u.deletedAt is null and u.id <> :exceptId")
  boolean existsOtherWithEmail(String email, UUID exceptId);

  List<AppUser> findByAssociationIdAndRoleAndDeletedAtIsNullOrderByName(
      UUID associationId, Role role);

  long countByAssociationIdAndRoleAndStatusAndDeletedAtIsNull(
      UUID associationId, Role role, UserStatus status);

  long countByRoleAndDeletedAtIsNull(Role role);

  List<AppUser> findByIdIn(Collection<UUID> ids);

  /** Para o pull: o próprio agricultor e os coordenadores da associação (doc 1 §4.8). */
  @Query(
      "select u from AppUser u where u.syncSeq > :cursor and (u.id = :userId"
          + " or (u.associationId = :associationId and u.role = :coordinator))"
          + " order by u.syncSeq")
  List<AppUser> findPeopleChanges(
      UUID userId, UUID associationId, Role coordinator, long cursor, Limit limit);
}
