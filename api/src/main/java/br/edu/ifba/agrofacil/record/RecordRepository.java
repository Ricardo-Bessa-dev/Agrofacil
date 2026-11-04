// Acesso aos registros de manejo (consultas polimórficas sobre a superclasse).
package br.edu.ifba.agrofacil.record;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface RecordRepository
    extends JpaRepository<ManagementRecord, UUID>, JpaSpecificationExecutor<ManagementRecord> {

  Optional<ManagementRecord> findByIdAndFarmerIdAndDeletedAtIsNull(UUID id, UUID farmerId);

  Optional<ManagementRecord> findByIdAndAssociationIdAndDeletedAtIsNull(
      UUID id, UUID associationId);

  @Query("select p from Planting p where p.id = :id and p.deletedAt is null")
  Optional<Planting> findActivePlanting(UUID id);

  List<ManagementRecord> findByIdIn(Collection<UUID> ids);

  /** Aplicações e colheitas não excluídas que apontam para o plantio (RN13). */
  @Query(
      "select count(r) from ManagementRecord r where r.deletedAt is null and ("
          + " r.id in (select h.id from Harvest h where h.plantingId = :plantingId)"
          + " or r.id in (select a.id from InputApplication a where a.plantingId = :plantingId))")
  long countDependents(UUID plantingId);

  /** Data da colheita final mais recente do plantio, se houver (RN12). */
  @Query(
      "select max(h.performedOn) from Harvest h where h.plantingId = :plantingId"
          + " and h.finalHarvest = true and h.deletedAt is null")
  LocalDate lastFinalHarvestOn(UUID plantingId);

  @Query(
      "select r from ManagementRecord r where r.farmerId = :farmerId and r.syncSeq > :cursor"
          + " order by r.syncSeq")
  List<ManagementRecord> findChanges(UUID farmerId, long cursor, Limit limit);
}
