// Assunto de um documento gerado: de quem ou do quê ele trata (doc 1 §3.16).
package br.edu.ifba.agrofacil.document;

import java.time.LocalDate;
import java.util.UUID;

/** Dois documentos do mesmo tipo com o mesmo assunto: o mais novo substitui o anterior (RN37). */
public record DocumentSubject(
    UUID farmerId,
    UUID propertyId,
    UUID groupId,
    UUID visitId,
    UUID certificationId,
    LocalDate from,
    LocalDate to) {}
