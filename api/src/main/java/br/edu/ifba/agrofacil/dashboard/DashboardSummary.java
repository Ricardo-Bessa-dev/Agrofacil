// Resumo do painel inicial do coordenador (doc 1 §3.11, RF19).
package br.edu.ifba.agrofacil.dashboard;

import br.edu.ifba.agrofacil.record.RecordEnums.RecordType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @param certifications quantidade por status da certificação atual de cada propriedade, mais
 *     {@code NONE} para as propriedades sem certificação
 * @param upcomingExpirations as próximas 10 que vencem em até 90 dias
 */
public record DashboardSummary(
    Farmers farmers,
    Properties properties,
    Map<String, Long> certifications,
    List<UpcomingExpiration> upcomingExpirations,
    Records records,
    long unmappedCatalogItems,
    Visits visits,
    Instant generatedAt) {

  public record Farmers(
      long active, long inactive, long withoutRecordsLast30Days, long withoutActiveDevice) {}

  public record Properties(long total, long withoutLocation) {}

  public record UpcomingExpiration(
      UUID certificationId,
      UUID propertyId,
      String propertyName,
      UUID farmerId,
      String farmerName,
      LocalDate expiresOn,
      long daysToExpire) {}

  /**
   * @param perWeek últimas 12 semanas, começando na segunda-feira
   */
  public record Records(
      long last30Days,
      Map<RecordType, Long> byTypeLast30Days,
      long incomplete,
      long openCorrectionRequests,
      List<WeekCount> perWeek) {}

  public record WeekCount(LocalDate weekStart, long count) {}

  /**
   * @param windowOpen propriedades com período de visitas aberto
   * @param urgent propriedades em situação URGENT (RN29)
   * @param resultMissing visitas com a data passada e sem resultado
   */
  public record Visits(long windowOpen, long urgent, long plannedNext30Days, long resultMissing) {}
}
