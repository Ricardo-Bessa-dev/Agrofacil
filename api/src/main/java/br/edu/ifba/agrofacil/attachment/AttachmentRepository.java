// Acesso aos anexos.
package br.edu.ifba.agrofacil.attachment;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

  List<Attachment> findByOwnerTypeAndOwnerIdAndDeletedAtIsNullOrderByCreatedAt(
      Attachment.OwnerType ownerType, UUID ownerId);

  List<Attachment> findByOwnerTypeAndOwnerIdInAndDeletedAtIsNull(
      Attachment.OwnerType ownerType, Collection<UUID> ownerIds);

  List<Attachment> findByOwnerTypeAndOwnerIdInAndKindAndDeletedAtIsNull(
      Attachment.OwnerType ownerType, Collection<UUID> ownerIds, Attachment.Kind kind);

  /** Fotos do registro: contam como nota fiscal na completude da compra (doc 1 §2.5). */
  @Query(
      "select count(a) > 0 from Attachment a where a.ownerType = 'RECORD' and a.ownerId = :recordId"
          + " and a.kind = 'PHOTO' and a.deletedAt is null")
  boolean recordHasPhoto(UUID recordId);

  /**
   * Pull: anexos dos registros, propriedades e certificações do agricultor, dos comunicados que ele
   * vê e das visitas em que participa ou que já aconteceram na roça dele (doc 1 §4.8).
   */
  @Query(
      "select a from Attachment a where a.syncSeq > :cursor and ("
          + " (a.ownerType = 'RECORD' and a.ownerId in"
          + "   (select r.id from ManagementRecord r where r.farmerId = :farmerId))"
          + " or (a.ownerType = 'PROPERTY' and a.ownerId in"
          + "   (select p.id from Property p where p.farmerId = :farmerId))"
          + " or (a.ownerType = 'CERTIFICATION' and a.ownerId in"
          + "   (select c.id from Certification c where c.propertyId in"
          + "     (select p2.id from Property p2 where p2.farmerId = :farmerId)))"
          + " or (a.ownerType = 'ANNOUNCEMENT' and a.ownerId in"
          + "   (select n.id from Announcement n where n.associationId = :associationId"
          + "     and (n.groupId is null or n.groupId = :groupId)))"
          + " or (a.ownerType = 'VISIT' and (a.ownerId in"
          + "   (select vp.visitId from VisitParticipant vp where vp.farmerId = :farmerId"
          + "     and vp.deletedAt is null)"
          + "   or a.ownerId in (select v.id from Visit v where v.status = 'DONE' and v.propertyId in"
          + "     (select p3.id from Property p3 where p3.farmerId = :farmerId)))))"
          + " order by a.syncSeq")
  List<Attachment> findChanges(
      UUID farmerId, UUID associationId, UUID groupId, long cursor, Limit limit);
}
