// Endpoints do painel para as certificações das propriedades (doc 1 §3.10).
package br.edu.ifba.agrofacil.certification;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Só o coordenador (regra geral do {@code SecurityConfig}); o agricultor recebe pelo pull. */
@Tag(name = "Certificações")
@RestController
public class CertificationController {

  private final CertificationService service;
  private final CurrentUserProvider currentUser;

  public CertificationController(CertificationService service, CurrentUserProvider currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @GetMapping("/certifications")
  public PageResponse<CertificationView> list(CertificationFilter filter, Pageable pageable) {
    return service.list(currentUser.get(), filter, pageable);
  }

  @GetMapping("/properties/{id}/certifications")
  public List<CertificationView> forProperty(@PathVariable UUID id) {
    return service.forProperty(currentUser.get(), id);
  }

  @PostMapping("/certifications")
  @ResponseStatus(HttpStatus.CREATED)
  public CertificationView create(@Valid @RequestBody CertificationWrite body) {
    return service.create(currentUser.get(), body);
  }

  @PostMapping("/certifications/{id}/renew")
  @ResponseStatus(HttpStatus.CREATED)
  public CertificationView renew(
      @PathVariable UUID id, @Valid @RequestBody CertificationWrite body) {
    return service.renew(currentUser.get(), id, body);
  }

  @GetMapping("/certifications/{id}")
  public CertificationView get(@PathVariable UUID id) {
    return service.get(currentUser.get(), id);
  }

  @PutMapping("/certifications/{id}")
  public CertificationView update(
      @PathVariable UUID id, @Valid @RequestBody CertificationWrite body) {
    return service.update(currentUser.get(), id, body);
  }

  @PostMapping("/certifications/{id}/suspend")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void suspend(@PathVariable UUID id, @Valid @RequestBody StatusReason body) {
    service.suspend(currentUser.get(), id, body.reason());
  }

  @PostMapping("/certifications/{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@PathVariable UUID id, @Valid @RequestBody StatusReason body) {
    service.cancel(currentUser.get(), id, body.reason());
  }

  @PostMapping("/certifications/{id}/reactivate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reactivate(@PathVariable UUID id) {
    service.reactivate(currentUser.get(), id);
  }

  @DeleteMapping("/certifications/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(currentUser.get(), id);
  }
}
