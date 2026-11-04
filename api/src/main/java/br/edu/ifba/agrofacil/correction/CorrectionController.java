// Endpoints do painel para pedidos de correção (doc 1 §3.9.3).
package br.edu.ifba.agrofacil.correction;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pedidos de correção")
@RestController
public class CorrectionController {

  private final CorrectionService service;
  private final CurrentUserProvider currentUser;

  public CorrectionController(CorrectionService service, CurrentUserProvider currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  public record CreateRequest(@NotBlank @Size(max = 500) String message) {}

  @PostMapping("/records/{id}/correction-requests")
  @ResponseStatus(HttpStatus.CREATED)
  public CorrectionRequestView create(
      @PathVariable UUID id, @Valid @RequestBody CreateRequest body) {
    return service.create(currentUser.get(), id, body.message());
  }

  @PostMapping("/correction-requests/{id}/resolve")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resolve(@PathVariable UUID id) {
    service.resolve(currentUser.get(), id);
  }

  @GetMapping("/correction-requests")
  public PageResponse<CorrectionRequestView> list(
      @RequestParam(required = false) CorrectionRequest.Status status,
      @RequestParam(required = false) UUID farmerId,
      @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return service.list(currentUser.get(), status, farmerId, pageable);
  }
}
