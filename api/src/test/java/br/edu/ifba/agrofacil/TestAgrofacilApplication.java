// Sobe a API localmente com um Postgres em container, sem precisar do Docker Compose.
package br.edu.ifba.agrofacil;

import org.springframework.boot.SpringApplication;

/** Rode esta classe pela IDE para usar o banco do Testcontainers no lugar do Compose. */
public class TestAgrofacilApplication {

  public static void main(String[] args) {
    SpringApplication.from(AgrofacilApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
