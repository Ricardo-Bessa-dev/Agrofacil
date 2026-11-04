// Acesso aos documentos gerados.
package br.edu.ifba.agrofacil.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GeneratedDocumentRepository
    extends JpaRepository<GeneratedDocument, UUID>, JpaSpecificationExecutor<GeneratedDocument> {

  Optional<GeneratedDocument> findByIdAndAssociationId(UUID id, UUID associationId);

  Optional<GeneratedDocument> findByVerificationCodeAndAssociationId(
      String code, UUID associationId);

  boolean existsByVerificationCode(String code);

  List<GeneratedDocument> findByAssociationIdAndKindAndStatusIn(
      UUID associationId, DocumentKind kind, List<GeneratedDocument.Status> statuses);
}
