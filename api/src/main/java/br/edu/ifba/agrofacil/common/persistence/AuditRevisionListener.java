// Preenche a revisão do Envers a partir do usuário autenticado na requisição atual.
package br.edu.ifba.agrofacil.common.persistence;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import org.hibernate.envers.RevisionListener;

public class AuditRevisionListener implements RevisionListener {

  @Override
  public void newRevision(Object revision) {
    var rev = (AuditRevision) revision;
    CurrentUserProvider.currentOrNull()
        .ifPresentOrElse(
            u -> rev.fill(u.id(), u.deviceId(), u.deviceId() != null ? "MOBILE" : "WEB"),
            () -> rev.fill(null, null, "SYSTEM"));
  }
}
