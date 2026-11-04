// Endpoints do painel para colaboradores, Comissão de Avaliação e impedimentos (doc 1 §3.15).
package br.edu.ifba.agrofacil.spg.people;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.spg.people.SpgPeopleDtos.CommitteeView;
import br.edu.ifba.agrofacil.spg.people.SpgPeopleDtos.CommitteeWrite;
import br.edu.ifba.agrofacil.spg.people.SpgPeopleDtos.ConflictView;
import br.edu.ifba.agrofacil.spg.people.SpgPeopleDtos.ConflictWrite;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Só o coordenador (regra geral do {@code SecurityConfig}). */
@Tag(name = "SPG: pessoas")
@RestController
public class SpgPeopleController {

  private final SpgPeopleService service;
  private final CurrentUserProvider currentUser;

  public SpgPeopleController(SpgPeopleService service, CurrentUserProvider currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @GetMapping("/collaborators")
  public List<CollaboratorDtos.View> collaborators() {
    return service.collaborators(currentUser.get());
  }

  @PostMapping("/collaborators")
  @ResponseStatus(HttpStatus.CREATED)
  public CollaboratorDtos.View createCollaborator(@Valid @RequestBody CollaboratorDtos.Write body) {
    return service.createCollaborator(currentUser.get(), body);
  }

  @PatchMapping("/collaborators/{id}")
  public CollaboratorDtos.View patchCollaborator(
      @PathVariable UUID id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = CollaboratorDtos.Write.class)))
          @RequestBody
          JsonNode body) {
    return service.patchCollaborator(currentUser.get(), id, body);
  }

  @GetMapping("/committee-members")
  public List<CommitteeView> committee() {
    return service.committee(currentUser.get());
  }

  @PostMapping("/committee-members")
  @ResponseStatus(HttpStatus.CREATED)
  public CommitteeView createCommitteeMember(@Valid @RequestBody CommitteeWrite body) {
    return service.createCommitteeMember(currentUser.get(), body);
  }

  @PatchMapping("/committee-members/{id}")
  public CommitteeView patchCommitteeMember(
      @PathVariable UUID id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = CommitteeWrite.class)))
          @RequestBody
          JsonNode body) {
    return service.patchCommitteeMember(currentUser.get(), id, body);
  }

  @DeleteMapping("/committee-members/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteCommitteeMember(@PathVariable UUID id) {
    service.deleteCommitteeMember(currentUser.get(), id);
  }

  @GetMapping("/member-conflicts")
  public List<ConflictView> conflicts() {
    return service.conflicts(currentUser.get());
  }

  @PostMapping("/member-conflicts")
  @ResponseStatus(HttpStatus.CREATED)
  public ConflictView createConflict(@Valid @RequestBody ConflictWrite body) {
    return service.createConflict(currentUser.get(), body);
  }

  @DeleteMapping("/member-conflicts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteConflict(@PathVariable UUID id) {
    service.deleteConflict(currentUser.get(), id);
  }
}
