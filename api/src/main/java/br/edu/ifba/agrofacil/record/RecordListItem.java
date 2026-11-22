// Linha da lista de registros do painel, com nomes já resolvidos (doc 1 §3.9.2).
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.common.domain.Area;
import br.edu.ifba.agrofacil.common.domain.Quantity;
import br.edu.ifba.agrofacil.common.web.IdName;
import br.edu.ifba.agrofacil.record.RecordEnums.Completeness;
import br.edu.ifba.agrofacil.record.RecordEnums.CycleStatus;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordSource;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param subject nome da cultura, do insumo ou do produto, incluindo o texto de "Outro"
 * @param planting cultura e ciclo, só nas linhas de plantio (nulo nos outros tipos)
 */
public record RecordListItem(
    UUID id,
    RecordType type,
    Long number,
    LocalDate performedOn,
    IdName farmer,
    IdName property,
    IdName plot,
    String subject,
    Quantity quantity,
    Area area,
    Completeness completeness,
    long openCorrectionRequests,
    RecordSource source,
    Instant updatedAt,
    PlantingInfo planting) {

  /** Dados para escolher um plantio ao registrar aplicação, colheita ou saída (RN11, RN42). */
  public record PlantingInfo(
      UUID cropId, String cropOtherName, CycleStatus cycleStatus, LocalDate finishedOn) {}
}
