// Endpoints do painel para os dados e configurações da associação (doc 1 §3.7.1).
package br.edu.ifba.agrofacil.association;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@Tag(name = "Associação")
@RestController
@RequestMapping("/association")
public class AssociationController {

  private final AssociationService service;
  private final CurrentUserProvider currentUser;

  public AssociationController(AssociationService service, CurrentUserProvider currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @GetMapping
  public AssociationView get() {
    return service.view(currentUser.get());
  }

  @Operation(summary = "Edição parcial; envie a version atual")
  @PatchMapping
  public AssociationView patch(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = AssociationWrite.class)))
          @RequestBody
          JsonNode body) {
    return service.patch(currentUser.get(), body);
  }
}
