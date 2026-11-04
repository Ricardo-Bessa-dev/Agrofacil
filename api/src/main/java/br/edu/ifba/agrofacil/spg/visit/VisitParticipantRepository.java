// Acesso aos participantes das visitas.
package br.edu.ifba.agrofacil.spg.visit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VisitParticipantRepository extends JpaRepository<VisitParticipant, UUID> {

  List<VisitParticipant> findByVisitIdAndDeletedAtIsNull(UUID visitId);

  List<VisitParticipant> findByVisitIdIn(Collection<UUID> visitIds);

  /** Visitas marcadas de cada pessoa num período (aviso de dia duplicado e equilíbrio, RN33/34). */
  @Query(
      "select vp.farmerId, vp.collaboratorId, v.plannedOn, v.id from VisitParticipant vp, Visit v"
          + " where v.id = vp.visitId and vp.deletedAt is null and v.deletedAt is null"
          + " and v.status = br.edu.ifba.agrofacil.spg.visit.VisitStatus.PLANNED"
          + " and v.associationId = :associationId and v.plannedOn between :from and :to")
  List<Object[]> plannedAgenda(
      UUID associationId, java.time.LocalDate from, java.time.LocalDate to);

  /**
   * Visitas concluídas desde a data, com os agricultores visitadores (rodízio e troca, RN33):
   * [farmerId do visitador, propertyId visitada, farmerId dono da propriedade].
   */
  @Query(
      "select vp.farmerId, v.propertyId, p.farmerId from VisitParticipant vp, Visit v, Property p"
          + " where v.id = vp.visitId and p.id = v.propertyId and vp.deletedAt is null"
          + " and v.deletedAt is null and vp.farmerId is not null"
          + " and vp.role = br.edu.ifba.agrofacil.spg.visit.VisitParticipant.Role.VISITOR"
          + " and v.status = br.edu.ifba.agrofacil.spg.visit.VisitStatus.DONE"
          + " and v.associationId = :associationId and v.doneOn >= :since")
  List<Object[]> doneVisitorPairs(UUID associationId, java.time.LocalDate since);
}
