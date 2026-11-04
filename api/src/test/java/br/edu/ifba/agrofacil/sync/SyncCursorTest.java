// Testes do cursor opaco do pull (doc 1 §4.8).
package br.edu.ifba.agrofacil.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SyncCursorTest {

  @Test
  void zero_isTheFirstPull() {
    assertThat(SyncCursor.parse("0")).contains(new SyncCursor(0, Instant.EPOCH));
  }

  @Test
  void formattedCursor_roundTrips() {
    var cursor = new SyncCursor(1234, Instant.ofEpochMilli(1_700_000_000_000L));
    assertThat(SyncCursor.parse(cursor.format())).contains(cursor);
  }

  @Test
  void garbage_isInvalid_andLeadsToReset() {
    assertThat(SyncCursor.parse("abc")).isEmpty();
    assertThat(SyncCursor.parse("12.x")).isEmpty();
    assertThat(SyncCursor.parse("-1.5")).isEmpty();
  }

  @Test
  void pageTracker_usesTheSmallestMaxAmongFullTables() {
    var tracker = new SyncPullService.PageTracker(2, 10);
    tracker.track(java.util.List.of(11L, 15L), Long::longValue); // cheia: máximo 15
    tracker.track(java.util.List.of(12L), Long::longValue);
    tracker.track(java.util.List.of(13L, 20L), Long::longValue); // cheia: máximo 20
    assertThat(tracker.hasMore()).isTrue();
    assertThat(tracker.nextCursor()).isEqualTo(15);
  }
}
