// Resultado da visita enviado pelo app do responsável pelo registro (mutação "visit", doc 2
// §1.15.4).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.sync.MutationHandler;
import br.edu.ifba.agrofacil.sync.SyncDtos;
import org.springframework.stereotype.Component;

/** Só {@code op = UPSERT}: mudar data, participantes ou cancelar é só no painel. */
@Component
public class VisitMutationHandler implements MutationHandler {

  private final VisitService visits;
  private final JsonPatch json;

  public VisitMutationHandler(VisitService visits, JsonPatch json) {
    this.visits = visits;
    this.json = json;
  }

  @Override
  public String entity() {
    return "visit";
  }

  @Override
  public Long apply(CurrentUser user, SyncDtos.Mutation m) {
    if (m.op() != SyncDtos.Op.UPSERT || m.data() == null) {
      throw ApiException.validation(
          "op", "NOT_SUPPORTED", "Pelo app, só dá para enviar o resultado.");
    }
    var write = json.convertAndValidate(m.data(), VisitDtos.ResultWrite.class);
    return visits.recordFromApp(user, m.entityId(), write);
  }
}
