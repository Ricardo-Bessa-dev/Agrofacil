// Controle de edição simultânea do painel: a version enviada precisa ser a atual (doc 1 §3.2.7).
package br.edu.ifba.agrofacil.common.web;

import br.edu.ifba.agrofacil.common.error.ApiException;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

public final class VersionCheck {

  private VersionCheck() {}

  /**
   * @param body corpo do PATCH; precisa trazer {@code version}
   * @throws ApiException 409 VERSION_CONFLICT se faltar ou estiver desatualizada
   */
  public static void ensure(JsonNode body, Long sent, Long current) {
    if (!body.has("version")) {
      throw ApiException.validation("version", "REQUIRED", "Envie a versão do item.");
    }
    ensure(sent, current);
  }

  /** Para PUT, em que o corpo já foi convertido. */
  public static void ensure(Long sent, Long current) {
    if (!Objects.equals(sent, current)) {
      throw ApiException.versionConflict();
    }
  }
}
