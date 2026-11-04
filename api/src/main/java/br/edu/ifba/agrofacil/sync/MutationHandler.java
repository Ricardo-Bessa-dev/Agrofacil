// Aplica uma mutação do push a um tipo de entidade (record, property, attachment, notification).
package br.edu.ifba.agrofacil.sync;

import br.edu.ifba.agrofacil.common.security.CurrentUser;

/** Erros de negócio sobem como ApiException e viram REJECTED no {@link SyncPushService}. */
public interface MutationHandler {

  /** Nome da entidade no contrato, ex.: {@code record}. */
  String entity();

  /**
   * @return a nova versão da entidade
   */
  Long apply(CurrentUser user, SyncDtos.Mutation mutation);
}
