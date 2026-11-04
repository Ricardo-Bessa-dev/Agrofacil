// Filtros da lista de visitas do painel (doc 1 §3.15).
package br.edu.ifba.agrofacil.spg.visit;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * @param from data marcada inicial, inclusiva
 * @param personId visitas em que a pessoa (agricultor ou colaborador) participa
 */
public record VisitFilter(
    UUID propertyId,
    UUID groupId,
    VisitType type,
    VisitStatus status,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
    UUID personId) {}
