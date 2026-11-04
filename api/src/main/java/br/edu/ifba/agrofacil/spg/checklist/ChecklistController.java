// Endpoints do roteiro da visita no catálogo do painel (doc 1 §3.15).
package br.edu.ifba.agrofacil.spg.checklist;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** O agricultor recebe o roteiro pelo pull; estas rotas são do coordenador. */
@Tag(name = "SPG: roteiro da visita")
@RestController
public class ChecklistController {

  private final ChecklistService service;
  private final CurrentUserProvider currentUser;

  public ChecklistController(ChecklistService service, CurrentUserProvider currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @GetMapping("/catalog/visit-checklist")
  public List<ChecklistDtos.View> list(
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "false") boolean includeInactive) {
    return service.list(currentUser.get(), type, includeInactive);
  }

  @PostMapping("/catalog/visit-checklist")
  @ResponseStatus(HttpStatus.CREATED)
  public ChecklistDtos.View create(@Valid @RequestBody ChecklistDtos.Write body) {
    return service.create(currentUser.get(), body);
  }

  @PatchMapping("/catalog/visit-checklist/{code}")
  public ChecklistDtos.View patch(
      @PathVariable String code,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = ChecklistDtos.Write.class)))
          @RequestBody
          JsonNode body) {
    return service.patch(currentUser.get(), code, body);
  }
}
