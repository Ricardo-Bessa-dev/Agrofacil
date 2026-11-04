// Endpoints do painel para comunicados e dicas (doc 1 §3.12); o agricultor recebe pelo pull.
package br.edu.ifba.agrofacil.announcement;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@Tag(name = "Comunicados")
@RestController
@RequestMapping("/announcements")
public class AnnouncementController {

  private final AnnouncementService service;
  private final CurrentUserProvider currentUser;

  public AnnouncementController(AnnouncementService service, CurrentUserProvider currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @GetMapping
  public PageResponse<AnnouncementView> list(
      @RequestParam(required = false) AnnouncementType type,
      @PageableDefault(sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return service.list(currentUser.get(), type, pageable);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AnnouncementView publish(@Valid @RequestBody AnnouncementWrite body) {
    return service.publish(currentUser.get(), body);
  }

  @PatchMapping("/{id}")
  public AnnouncementView patch(
      @PathVariable UUID id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = AnnouncementWrite.class)))
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
