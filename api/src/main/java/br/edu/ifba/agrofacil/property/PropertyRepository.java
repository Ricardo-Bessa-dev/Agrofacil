// Acesso às propriedades.
package br.edu.ifba.agrofacil.property;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PropertyRepository
    extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {

  Optional<Property> findByIdAndAssociationIdAndDeletedAtIsNull(UUID id, UUID associationId);

  Optional<Property> findByIdAndFarmerIdAndDeletedAtIsNull(UUID id, UUID farmerId);

  List<Property> findByFarmerIdAndDeletedAtIsNullOrderByName(UUID farmerId);

  List<Property> findByFarmerIdInAndDeletedAtIsNull(Collection<UUID> farmerIds);

  @Query(
      "select p from Property p where p.associationId = :associationId and p.deletedAt is null"
          + " and p.location.latitude is not null")
  List<Property> findWithLocation(UUID associationId);

  @Query(
      "select p from Property p where p.farmerId = :farmerId and p.syncSeq > :cursor order by p.syncSeq")
  List<Property> findChanges(UUID farmerId, long cursor, Limit limit);

  /** Troca de dono: os registros acompanham a propriedade (doc 1 §2.3.8). */
  @Modifying
  @Query(
      value =
          "update management_record set farmer_id = :farmerId, updated_at = now(), version = version + 1"
              + " where property_id = :propertyId",
      nativeQuery = true)
  int moveRecordsToFarmer(UUID propertyId, UUID farmerId);
}
