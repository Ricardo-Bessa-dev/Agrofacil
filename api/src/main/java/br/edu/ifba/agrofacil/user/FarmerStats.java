// Indicadores da lista de agricultores calculados em lote, com uma consulta por indicador.
package br.edu.ifba.agrofacil.user;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class FarmerStats {

  private final JdbcClient jdbc;

  public FarmerStats(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * @param lastRecordOn data do registro mais recente
   * @param incompleteRecords registros com completude {@code INCOMPLETE}
   */
  public record RecordStats(LocalDate lastRecordOn, long incompleteRecords) {}

  /** Registros por agricultor (excluídos não contam). */
  public Map<UUID, RecordStats> records(Collection<UUID> farmerIds) {
    var result = new HashMap<UUID, RecordStats>();
    if (farmerIds.isEmpty()) {
      return result;
    }
    jdbc.sql(
            """
            select farmer_id, max(performed_on) as last_on,
                   count(*) filter (where completeness = 'INCOMPLETE') as incomplete
            from management_record
            where deleted_at is null and farmer_id in (:ids)
            group by farmer_id
            """)
        .param("ids", farmerIds)
        .query((rs, i) -> {
          Date last = rs.getDate("last_on");
          result.put(
              rs.getObject("farmer_id", UUID.class),
              new RecordStats(last == null ? null : last.toLocalDate(), rs.getLong("incomplete")));
          return null;
        })
        .list();
    return result;
  }
}
