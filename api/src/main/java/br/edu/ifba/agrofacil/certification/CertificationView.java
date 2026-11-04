// Certificação no formato do contrato (doc 1 §3.10), igual para o painel e para o pull do app.
package br.edu.ifba.agrofacil.certification;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param status calculado (doc 1 §2.3.7), nunca gravado
 * @param daysToExpire negativo se já venceu
 * @param isCurrent se é a certificação atual da propriedade
 * @param documentAttachmentId PDF do certificado, se já foi enviado
 */
public record CertificationView(
    UUID id,
    UUID propertyId,
    String number,
    LocalDate issuedOn,
    LocalDate expiresOn,
    String scope,
    List<CertificationWrite.Product> products,
    List<UUID> workerIds,
    String notes,
    UUID previousId,
    CertificationStatus status,
    long daysToExpire,
    String statusReason,
    boolean isCurrent,
    UUID documentAttachmentId,
    PropertyRef property,
    Long version) {

  /** Propriedade e agricultor, para o painel não precisar de outra consulta. */
  public record PropertyRef(UUID id, String name, UUID farmerId, String farmerName) {}

  /** Formato de escrita com os valores atuais (base do PUT e da renovação). */
  public CertificationWrite toWrite() {
    return new CertificationWrite(
        propertyId, number, issuedOn, expiresOn, scope, products, workerIds, notes, version);
  }
}
