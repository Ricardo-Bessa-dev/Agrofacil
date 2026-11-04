// Relógio dos testes: começa no horário real e pode ser adiantado.
package br.edu.ifba.agrofacil.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class MutableClock extends Clock {

  private Instant now = Instant.now();

  public synchronized void advance(Duration duration) {
    now = now.plus(duration);
  }

  public synchronized void reset() {
    now = Instant.now();
  }

  @Override
  public synchronized Instant instant() {
    return now;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    MutableClock self = this;
    return new Clock() {
      @Override
      public ZoneId getZone() {
        return zone;
      }

      @Override
      public Clock withZone(ZoneId other) {
        return self.withZone(other);
      }

      @Override
      public Instant instant() {
        return self.instant();
      }
    };
  }
}
