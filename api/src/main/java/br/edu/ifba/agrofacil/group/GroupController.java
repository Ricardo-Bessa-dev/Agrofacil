// Endpoints do painel para os grupos do SPG (doc 1 §3.7.3).
package br.edu.ifba.agrofacil.group;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@Tag(name = "Grupos do SPG")
@RestController
@RequestMapping("/groups")
public class GroupController {

  private final GroupService service;
  private final CurrentUserProvider currentUser;

  public GroupController(GroupService service, CurrentUserProvider currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @GetMapping
  public List<GroupView> list() {
    return service.list(currentUser.get());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public GroupView create(@Valid @RequestBody GroupWrite body) {
    return service.create(currentUser.get(), body);
  }

  @GetMapping("/{id}")
  public GroupDetail get(@PathVariable UUID id) {
    return service.get(currentUser.get(), id);
  }

  @PatchMapping("/{id}")
  public GroupView patch(
      @PathVariable UUID id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = GroupWrite.class)))
          @RequestBody
          JsonNode body) {
    return service.patch(currentUser.get(), id, body);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(currentUser.get(), id);
  }
}
