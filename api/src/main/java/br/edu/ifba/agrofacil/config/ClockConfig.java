// Relógio único da aplicação, para que os testes possam controlar "agora" e "hoje".
package br.edu.ifba.agrofacil.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

  /** Relógio em UTC; "hoje" na associação é calculado com {@link AppProperties#zone()}. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
