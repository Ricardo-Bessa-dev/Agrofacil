// Acesso às perguntas do roteiro da visita.
package br.edu.ifba.agrofacil.spg.checklist;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VisitChecklistRepository extends JpaRepository<VisitChecklistItem, UUID> {

  /** Modelo padrão (association_id nulo) e as perguntas próprias da associação. */
  @Query(
      "select i from VisitChecklistItem i"
          + " where i.associationId is null or i.associationId = :associationId")
  List<VisitChecklistItem> findVisible(UUID associationId);

  /** Pull: mesmas linhas, depois do cursor (doc 1 §4.8). */
  @Query(
      "select i from VisitChecklistItem i where (i.associationId is null"
          + " or i.associationId = :associationId) and i.syncSeq > :cursor order by i.syncSeq")
  List<VisitChecklistItem> findChanges(UUID associationId, long cursor, Limit limit);
}
