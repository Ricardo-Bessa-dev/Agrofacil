// Endpoints de notificações do coordenador (doc 1 §3.12); o agricultor recebe as dele pelo pull.
package br.edu.ifba.agrofacil.notification;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notificações")
@RestController
@RequestMapping("/notifications")
public class NotificationController {

  private final NotificationService service;
  private final CurrentUserProvider currentUser;

  public NotificationController(NotificationService service, CurrentUserProvider currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @GetMapping
  public PageResponse<NotificationView> list(
      @RequestParam(defaultValue = "false") boolean unreadOnly,
      @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return service.list(currentUser.get(), unreadOnly, pageable);
  }

  @PostMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void read(@PathVariable UUID id) {
    service.markRead(currentUser.get(), id);
  }

  @PostMapping("/read-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void readAll() {
    service.markAllRead(currentUser.get());
  }
}
