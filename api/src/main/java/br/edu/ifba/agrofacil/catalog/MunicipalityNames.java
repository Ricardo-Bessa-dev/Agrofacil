// Resolve nomes de municípios pelo código IBGE, com cache em memória (a tabela não muda).
package br.edu.ifba.agrofacil.catalog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class MunicipalityNames {

  private final MunicipalityRepository repository;
  private final Map<Integer, String> cache = new ConcurrentHashMap<>();

  public MunicipalityNames(MunicipalityRepository repository) {
    this.repository = repository;
  }

  /** Nome do município, ou {@code null} se o código não existir. */
  public String nameOf(int ibgeCode) {
    return cache.computeIfAbsent(
        ibgeCode, id -> repository.findById(id).map(Municipality::getName).orElse(null));
  }

  public boolean exists(int ibgeCode) {
    return nameOf(ibgeCode) != null;
  }
}
