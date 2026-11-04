// Postgres real em container, com a mesma versão do Docker Compose.
package br.edu.ifba.agrofacil;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  /** Mesma imagem de infra/docker-compose.dev.yml, para não testar contra outra versão. */
  static final String POSTGRES_IMAGE = "postgres:17.11-alpine";

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));
  }
}
