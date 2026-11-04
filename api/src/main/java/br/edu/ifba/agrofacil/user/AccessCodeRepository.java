// Acesso aos códigos de ativação.
package br.edu.ifba.agrofacil.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessCodeRepository extends JpaRepository<AccessCode, UUID> {

  /** Código mais recente ainda não usado nem revogado (pode estar vencido ou bloqueado). */
  Optional<AccessCode> findFirstByFarmerIdAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
      UUID farmerId);

  List<AccessCode> findByFarmerIdAndUsedAtIsNullAndRevokedAtIsNull(UUID farmerId);
}
