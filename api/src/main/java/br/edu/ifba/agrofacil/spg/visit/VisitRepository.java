// Acesso às visitas do SPG.
package br.edu.ifba.agrofacil.spg.visit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface VisitRepository
    extends JpaRepository<Visit, UUID>, JpaSpecificationExecutor<Visit> {

  Optional<Visit> findByIdAndAssociationIdAndDeletedAtIsNull(UUID id, UUID associationId);

  List<Visit> findByPropertyIdAndStatusAndDeletedAtIsNullOrderByDoneOnDesc(
      UUID propertyId, VisitStatus status);

  List<Visit> findByAssociationIdAndStatusAndPlannedOnBetweenAndDeletedAtIsNull(
      UUID associationId, VisitStatus status, LocalDate from, LocalDate to);

  /** Marcadas de todas as associações numa data (rotina de avisos). */
  List<Visit> findByStatusAndPlannedOnAndDeletedAtIsNull(VisitStatus status, LocalDate plannedOn);

  /** Marcadas com a data já passada (rotina de avisos e "resultado atrasado"). */
  List<Visit> findByStatusAndPlannedOnBeforeAndDeletedAtIsNull(VisitStatus status, LocalDate day);

  /** Marcadas a partir de hoje até a data (resumo diário, RN36). */
  List<Visit> findByStatusAndPlannedOnBetweenAndDeletedAtIsNull(
      VisitStatus status, LocalDate from, LocalDate to);

  /**
   * Pull do agricultor (doc 1 §4.8): (a) visitas da propriedade dele, menos as sem aviso ainda não
   * concluídas (RN31); (b) visitas em que ele é ou foi participante (a lápide sai quando ele deixa
   * de ser). Inclui as excluídas, que viram lápide.
   */
  @Query(
      "select v from Visit v where v.syncSeq > :cursor and ("
          + " (v.propertyId in (select p.id from Property p where p.farmerId = :farmerId)"
          + "   and (v.unannounced = false or v.status = br.edu.ifba.agrofacil.spg.visit.VisitStatus.DONE))"
          + " or v.id in (select vp.visitId from VisitParticipant vp where vp.farmerId = :farmerId))"
          + " order by v.syncSeq")
  List<Visit> findChanges(UUID farmerId, long cursor, Limit limit);
}
