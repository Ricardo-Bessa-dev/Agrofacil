// Acesso aos tokens de convite e de redefinição de senha.
package br.edu.ifba.agrofacil.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

  Optional<UserToken> findByTokenHashAndPurpose(String tokenHash, UserToken.Purpose purpose);
}
