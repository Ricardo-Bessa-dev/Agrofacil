// Endpoints do painel para coordenadores (doc 1 §3.7.2).
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Coordenadores")
@RestController
@RequestMapping("/coordinators")
public class CoordinatorController {

  private final CoordinatorService service;
  private final CurrentUserProvider currentUser;

  public CoordinatorController(CoordinatorService service, CurrentUserProvider currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  public record InvitationRequest(
      @NotBlank @Size(max = 120) String name, @NotBlank @Email @Size(max = 120) String email) {}

  public record StatusRequest(@NotNull UserStatus status) {}

  @GetMapping
  public List<CoordinatorView> list() {
    return service.list(currentUser.get());
  }

  @PostMapping("/invitations")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void invite(@Valid @RequestBody InvitationRequest body) {
    service.invite(currentUser.get(), body.name(), body.email());
  }

  @PatchMapping("/{id}")
  public CoordinatorView changeStatus(
      @PathVariable UUID id, @Valid @RequestBody StatusRequest body) {
    return service.changeStatus(currentUser.get(), id, body.status());
  }
}
