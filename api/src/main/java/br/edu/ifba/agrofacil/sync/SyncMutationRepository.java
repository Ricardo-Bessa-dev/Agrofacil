// Acesso às mutações já processadas.
package br.edu.ifba.agrofacil.sync;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncMutationRepository extends JpaRepository<SyncMutation, UUID> {}
