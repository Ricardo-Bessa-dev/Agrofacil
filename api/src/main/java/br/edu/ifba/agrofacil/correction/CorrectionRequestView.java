// Pedido de correção no formato do contrato (doc 1 §3.9.3).
package br.edu.ifba.agrofacil.correction;

import java.time.Instant;
import java.util.UUID;

public record CorrectionRequestView(
    UUID id,
    UUID recordId,
    String message,
    CorrectionRequest.Status status,
    UUID createdById,
    Instant createdAt,
    UUID resolvedById,
    Instant resolvedAt) {

  public static CorrectionRequestView of(CorrectionRequest c) {
    return new CorrectionRequestView(
        c.getId(),
        c.getRecordId(),
        c.getMessage(),
        c.getStatus(),
        c.getCreatedBy(),
        c.getCreatedAt(),
        c.getResolvedBy(),
        c.getResolvedAt());
  }
}
