// Ponto de entrada da API do Agrofácil.
package br.edu.ifba.agrofacil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Sobe a aplicação Spring Boot e registra as classes de configuração {@code app.*}. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AgrofacilApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgrofacilApplication.class, args);
  }
}
