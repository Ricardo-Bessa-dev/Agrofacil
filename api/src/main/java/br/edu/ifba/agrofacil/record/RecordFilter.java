// Filtros da lista de registros do painel (doc 1 §3.9.2).
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.record.RecordEnums.Completeness;
import br.edu.ifba.agrofacil.record.RecordEnums.CycleStatus;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordSource;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordType;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * @param from data inicial, inclusiva
 * @param to data final, inclusiva
 * @param search procura no nome do agricultor e da propriedade, sem acento
 */
public record RecordFilter(
    UUID farmerId,
    UUID propertyId,
    UUID plotId,
    RecordType type,
    CycleStatus cycleStatus,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
    Completeness completeness,
    Boolean hasOpenCorrection,
    RecordSource source,
    String search) {}
