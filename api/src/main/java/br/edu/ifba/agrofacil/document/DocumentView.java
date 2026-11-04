// Documento gerado no formato do contrato (doc 1 §3.16).
package br.edu.ifba.agrofacil.document;

import br.edu.ifba.agrofacil.common.web.IdName;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param verificationCode como sai no rodapé, ex.: K7QM-4TZP
 */
public record DocumentView(
    UUID id,
    DocumentKind kind,
    String title,
    String templateVersion,
    DocumentSubject subject,
    String verificationCode,
    String sha256,
    long sizeBytes,
    GeneratedDocument.Status status,
    List<String> warnings,
    UUID signedAttachmentId,
    UUID supersededById,
    String cancelReason,
    IdName generatedBy,
    Instant generatedAt) {}
