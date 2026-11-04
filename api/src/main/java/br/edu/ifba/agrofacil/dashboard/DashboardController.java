// Endpoint do painel inicial do coordenador (doc 1 §3.11).
package br.edu.ifba.agrofacil.dashboard;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** O painel chama ao abrir e a cada 60 s enquanto a aba está visível. */
@Tag(name = "Painel inicial")
@RestController
public class DashboardController {

  private final DashboardService service;
  private final CurrentUserProvider currentUser;

  public DashboardController(DashboardService service, CurrentUserProvider currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @GetMapping("/dashboard/summary")
  public DashboardSummary summary() {
    return service.summary(currentUser.get());
  }
}
