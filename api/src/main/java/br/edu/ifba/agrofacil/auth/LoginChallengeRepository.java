// Acesso aos desafios de 2FA.
package br.edu.ifba.agrofacil.auth;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, UUID> {}
