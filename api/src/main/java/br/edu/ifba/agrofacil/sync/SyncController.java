// Endpoints de sincronização do app do agricultor (doc 1 §3.5 e Parte 4).
package br.edu.ifba.agrofacil.sync;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.user.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Não é transacional: o push controla uma transação por mutação. */
@Tag(name = "Sincronização")
@RestController
@RequestMapping("/sync")
public class SyncController {

  private final SyncPushService push;
  private final SyncPullService pull;
  private final DeviceService devices;
  private final CurrentUserProvider currentUser;

  public SyncController(
      SyncPushService push,
      SyncPullService pull,
      DeviceService devices,
      CurrentUserProvider currentUser) {
    this.push = push;
    this.pull = pull;
    this.devices = devices;
    this.currentUser = currentUser;
  }

  @Operation(summary = "Envia até 100 mutações; reenviar o mesmo mutationId é seguro")
  @PostMapping("/push")
  public SyncDtos.PushResponse push(@Valid @RequestBody SyncDtos.PushRequest body) {
    return push.push(currentUser.get(), body);
  }

  @Operation(summary = "Mudanças depois do cursor; repita enquanto hasMore for true")
  @GetMapping("/pull")
  public SyncDtos.PullResponse pull(
      @RequestParam(defaultValue = "0") String cursor,
      @RequestParam(defaultValue = "" + SyncPullService.DEFAULT_LIMIT) int limit) {
    var user = currentUser.get();
    devices.touch(user, user.deviceId()); // também barra aparelho revogado
    return pull.pull(user, cursor, limit);
  }
}
