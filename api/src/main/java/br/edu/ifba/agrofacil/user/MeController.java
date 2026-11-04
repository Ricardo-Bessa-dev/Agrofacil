// Endpoints do perfil do usuário logado (doc 1 §3.4).
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@Tag(name = "Perfil")
@RestController
@RequestMapping("/me")
public class MeController {

  private final MeService meService;
  private final DeviceService deviceService;
  private final CurrentUserProvider currentUser;

  public MeController(
      MeService meService, DeviceService deviceService, CurrentUserProvider currentUser) {
    this.meService = meService;
    this.deviceService = deviceService;
    this.currentUser = currentUser;
  }

  public record TermsAccept(@NotBlank String version) {}

  public record PasswordChange(@NotBlank String currentPassword, @NotBlank String newPassword) {}

  @GetMapping
  public Me get() {
    return meService.get(currentUser.get());
  }

  @PatchMapping
  public Me patch(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = MeWrite.class)))
          @RequestBody
          JsonNode body) {
    return meService.patch(currentUser.get(), body);
  }

  @PostMapping("/terms/accept")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void acceptTerms(@Valid @RequestBody TermsAccept body) {
    meService.acceptTerms(currentUser.get(), body.version());
  }

  /** Só o coordenador tem senha. */
  @PostMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(@Valid @RequestBody PasswordChange body) {
    var user = currentUser.get();
    if (user.isFarmer()) {
      throw new org.springframework.security.access.AccessDeniedException("só coordenador");
    }
    meService.changePassword(user, body.currentPassword(), body.newPassword());
  }

  /** Chamado pelo app ao abrir: token de push, versão e nome do aparelho. */
  @PutMapping("/device")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateDevice(@Valid @RequestBody MeDeviceUpdate body) {
    deviceService.updateCurrent(currentUser.get(), body);
  }
}
