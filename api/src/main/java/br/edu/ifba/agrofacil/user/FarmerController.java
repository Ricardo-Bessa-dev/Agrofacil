// Endpoints do painel para agricultores, códigos de acesso e aparelhos (doc 1 §3.7.4).
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

@Tag(name = "Agricultores")
@RestController
public class FarmerController {

  private final FarmerService farmers;
  private final DeviceService devices;
  private final CurrentUserProvider currentUser;

  public FarmerController(
      FarmerService farmers, DeviceService devices, CurrentUserProvider currentUser) {
    this.farmers = farmers;
    this.devices = devices;
    this.currentUser = currentUser;
  }

  @GetMapping("/farmers")
  public PageResponse<FarmerListItem> list(
      FarmerFilter filter, @PageableDefault(sort = "name") Pageable pageable) {
    return farmers.list(currentUser.get(), filter, pageable);
  }

  @PostMapping("/farmers")
  @ResponseStatus(HttpStatus.CREATED)
  public FarmerView create(@Valid @RequestBody FarmerWrite body) {
    return farmers.create(currentUser.get(), body);
  }

  @GetMapping("/farmers/{id}")
  public FarmerView get(@PathVariable UUID id) {
    return farmers.get(currentUser.get(), id);
  }

  @PatchMapping("/farmers/{id}")
  public FarmerView patch(
      @PathVariable UUID id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = FarmerWrite.class)))
          @RequestBody
          JsonNode body) {
    return farmers.patch(currentUser.get(), id, body);
  }

  @Operation(summary = "Desativa e revoga os aparelhos (RN25)")
  @PostMapping("/farmers/{id}/deactivate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID id) {
    farmers.deactivate(currentUser.get(), id);
  }

  @PostMapping("/farmers/{id}/reactivate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reactivate(@PathVariable UUID id) {
    farmers.reactivate(currentUser.get(), id);
  }

  @Operation(summary = "Gera um código de acesso; o código só aparece nesta resposta (RN03)")
  @PostMapping("/farmers/{id}/access-codes")
  @ResponseStatus(HttpStatus.CREATED)
  public AccessCodeResponse newAccessCode(@PathVariable UUID id) {
    return farmers.newAccessCode(currentUser.get(), id);
  }

  @GetMapping("/farmers/{id}/devices")
  public List<DeviceView> devices(@PathVariable UUID id) {
    return devices.listForFarmer(currentUser.get(), id);
  }

  @DeleteMapping("/devices/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeDevice(@PathVariable UUID id) {
    devices.revokeByCoordinator(currentUser.get(), id);
  }
}
