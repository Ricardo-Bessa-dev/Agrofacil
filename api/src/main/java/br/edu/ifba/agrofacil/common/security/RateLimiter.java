// Limita tentativas por chave (CPF, e-mail, IP) em memória; suficiente para uma única instância da
// API.
package br.edu.ifba.agrofacil.common.security;

import br.edu.ifba.agrofacil.common.error.ApiException;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Cada chave tem seu balde; ao estourar, responde 429 com {@code Retry-After}. */
@Component
public class RateLimiter {

  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  /**
   * Consome uma tentativa da chave.
   *
   * @param key ex.: {@code activate:cpf:12345678909}
   * @param capacity tentativas permitidas por período
   * @param period janela de tempo
   */
  public void check(String key, int capacity, Duration period) {
    var bucket =
        buckets.computeIfAbsent(
            key + "|" + capacity + "|" + period,
            k ->
                Bucket.builder()
                    .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                    .build());
    var probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      throw ApiException.rateLimited(
          Duration.ofNanos(probe.getNanosToWaitForRefill()).plusSeconds(1));
    }
  }

  /** Limpa os contadores (usado nos testes). */
  public void reset() {
    buckets.clear();
  }
}
