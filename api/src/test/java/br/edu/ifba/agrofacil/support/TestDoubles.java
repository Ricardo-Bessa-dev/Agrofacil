// Dublês dos testes de integração: relógio controlável e e-mail capturado.
package br.edu.ifba.agrofacil.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestDoubles {

  @Bean
  @Primary
  MutableClock testClock() {
    return new MutableClock();
  }

  @Bean
  @Primary
  CapturingMailGateway testMailGateway() {
    return new CapturingMailGateway();
  }
}
