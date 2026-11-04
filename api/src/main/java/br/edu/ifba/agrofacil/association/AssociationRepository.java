// Acesso às associações.
package br.edu.ifba.agrofacil.association;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssociationRepository extends JpaRepository<Association, UUID> {}
