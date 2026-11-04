// Filtros da lista de documentos gerados (doc 1 §3.16).
package br.edu.ifba.agrofacil.document;

import java.util.UUID;

public record DocumentFilter(
    DocumentKind kind,
    GeneratedDocument.Status status,
    UUID farmerId,
    UUID propertyId,
    UUID groupId,
    UUID visitId) {}
