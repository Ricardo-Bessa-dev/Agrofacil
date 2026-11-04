// Uma revisão do histórico de um registro (doc 1 §3.9.2), montada a partir do Envers.
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.common.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param by quem fez; nulo nas alterações feitas por rotinas do sistema
 * @param source MOBILE, WEB ou SYSTEM
 */
public record RecordHistoryEntry(
    long revision,
    Instant at,
    Author by,
    String source,
    ChangeType changeType,
    List<Change> changes) {

  public enum ChangeType {
    CREATED,
    UPDATED,
    DELETED
  }

  public record Author(UUID id, String name, Role role) {}

  /** Valores antes e depois, no formato JSON do contrato. */
  public record Change(String field, Object before, Object after) {}
}
