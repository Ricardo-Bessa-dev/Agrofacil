// Cursor opaco do pull: o sync_seq e o momento em que foi emitido (para detectar reset).
package br.edu.ifba.agrofacil.sync;

import java.time.Instant;
import java.util.Optional;

/**
 * Formato {@code <syncSeq>.<epochMillis>}. O cliente trata como texto opaco; "0" é o primeiro pull.
 *
 * @param issuedAt quando o cursor foi emitido; comparado com {@code app_user.sync_reset_at}
 */
public record SyncCursor(long seq, Instant issuedAt) {

  /** Lê o cursor; vazio se for inválido (o pull então responde {@code reset}). */
  public static Optional<SyncCursor> parse(String raw) {
    if (raw == null || raw.isBlank() || raw.equals("0")) {
      return Optional.of(new SyncCursor(0, Instant.EPOCH));
    }
    String[] parts = raw.split("\\.");
    if (parts.length != 2) {
      return Optional.empty();
    }
    try {
      long seq = Long.parseLong(parts[0]);
      long millis = Long.parseLong(parts[1]);
      return seq < 0
          ? Optional.empty()
          : Optional.of(new SyncCursor(seq, Instant.ofEpochMilli(millis)));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  public String format() {
    return seq + "." + issuedAt.toEpochMilli();
  }
}
