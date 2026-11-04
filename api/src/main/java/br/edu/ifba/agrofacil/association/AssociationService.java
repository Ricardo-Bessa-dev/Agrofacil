// Leitura e edição dos dados e configurações da associação do usuário.
package br.edu.ifba.agrofacil.association;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.FieldErrorItem;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.common.web.VersionCheck;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class AssociationService {

  private final AssociationRepository repository;
  private final JsonPatch jsonPatch;

  public AssociationService(AssociationRepository repository, JsonPatch jsonPatch) {
    this.repository = repository;
    this.jsonPatch = jsonPatch;
  }

  /** Associação pelo id; usada pelos outros módulos (ex.: tamanho da tarefa). */
  @Transactional(readOnly = true)
  public Association get(UUID associationId) {
    return repository.findById(associationId).orElseThrow(ApiException::notFound);
  }

  /** Dados da associação do usuário logado. */
  @Transactional(readOnly = true)
  public AssociationView view(CurrentUser user) {
    return AssociationView.of(get(user.associationId()));
  }

  /**
   * Edição parcial pelo coordenador. Exige a {@code version} atual (doc 1 §3.2.7).
   *
   * @throws ApiException 409 VERSION_CONFLICT se outra pessoa alterou antes
   */
  @Transactional
  public AssociationView patch(CurrentUser user, JsonNode body) {
    var association = get(user.associationId());
    var write =
        jsonPatch.merge(AssociationView.of(association).toWrite(), body, AssociationWrite.class);
    VersionCheck.ensure(body, write.version(), association.getVersion());
    if (new HashSet<>(write.alertDays()).size() != write.alertDays().size()) {
      throw ApiException.validation(
          List.of(
              new FieldErrorItem(
                  "alertDays", "DUPLICATED", "Os dias de aviso não podem repetir.")));
    }
    association.update(write);
    return AssociationView.of(repository.saveAndFlush(association));
  }
}
