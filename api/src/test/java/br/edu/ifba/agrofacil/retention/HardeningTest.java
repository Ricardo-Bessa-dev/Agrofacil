// Endurecimento (M11): rotina de retenção e cabeçalhos de segurança das respostas.
package br.edu.ifba.agrofacil.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import br.edu.ifba.agrofacil.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;

class HardeningTest extends IntegrationTest {

  @Autowired private DataRetentionJob retention;
  @Autowired private JdbcClient jdbc;

  private void mutationAt(UUID id, UUID userId, Duration ago) {
    jdbc.sql(
            "insert into sync_mutation (id, user_id, entity, entity_id, status, response,"
                + " processed_at) values (?, ?, 'record', ?, 'APPLIED', '{}'::jsonb, ?)")
        .params(id, userId, UUID.randomUUID(), Timestamp.from(clock.instant().minus(ago)))
        .update();
  }

  private boolean exists(UUID id) {
    return jdbc.sql("select count(*) from sync_mutation where id = ?")
            .param(id)
            .query(Long.class)
            .single()
        > 0;
  }

  @Test
  void retention_deletesOldSyncMutations_andKeepsRecentOnes() {
    var farmer = fixtures.farmer("Retenção Teste");
    var old = UUID.randomUUID();
    var recent = UUID.randomUUID();
    mutationAt(old, farmer.user().getId(), Duration.ofDays(91));
    mutationAt(recent, farmer.user().getId(), Duration.ofDays(5));

    var result = retention.run();

    assertThat(result)
        .containsKeys("access_code", "login_challenge", "user_token", "refresh_token");
    assertThat(exists(old)).isFalse();
    assertThat(exists(recent)).isTrue();
  }

  @Test
  void responses_carrySecurityHeaders() throws Exception {
    var coordinator = fixtures.coordinator();
    call(HttpMethod.GET, "/me", coordinator.accessToken(), null)
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().exists("Cache-Control"));
  }
}
