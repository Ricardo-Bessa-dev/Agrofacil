// Acesso às certificações.
package br.edu.ifba.agrofacil.certification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CertificationRepository extends JpaRepository<Certification, UUID> {

  Optional<Certification> findByIdAndDeletedAtIsNull(UUID id);

  List<Certification> findByPropertyIdInAndDeletedAtIsNull(Collection<UUID> propertyIds);

  List<Certification> findByPropertyIdAndDeletedAtIsNullOrderByIssuedOnDesc(UUID propertyId);

  /** Certificações das propriedades (não excluídas) da associação, para a lista do painel. */
  @Query(
      "select c from Certification c where c.deletedAt is null and c.propertyId in"
          + " (select p.id from Property p where p.associationId = :associationId"
          + " and p.deletedAt is null)")
  List<Certification> findAliveInAssociation(UUID associationId);

  /** Candidatas aos alertas (RN20): não excluídas e não canceladas, de propriedades ativas. */
  @Query(
      "select c from Certification c where c.deletedAt is null"
          + " and c.manualStatus <> br.edu.ifba.agrofacil.certification.ManualStatus.CANCELLED"
          + " and c.propertyId in (select p.id from Property p where p.deletedAt is null)")
  List<Certification> findAlertCandidates();

  /** Pull: certificações das propriedades do agricultor, inclusive as excluídas (lápides). */
  @Query(
      "select c from Certification c where c.syncSeq > :cursor and c.propertyId in"
          + " (select p.id from Property p where p.farmerId = :farmerId) order by c.syncSeq")
  List<Certification> findChanges(UUID farmerId, long cursor, Limit limit);
}
