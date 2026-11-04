// Endpoints do painel para planejamento, sugestão, agenda e resultado das visitas (doc 1 §3.15).
package br.edu.ifba.agrofacil.spg.visit;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import br.edu.ifba.agrofacil.spg.visit.VisitDtos.VisitView;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Só o coordenador (regra geral do {@code SecurityConfig}); o app usa a sincronização. */
@Tag(name = "SPG: visitas")
@RestController
public class VisitController {

  private final VisitService visits;
  private final VisitStatusService statuses;
  private final VisitorSuggestionService suggestions;
  private final CurrentUserProvider currentUser;

  public VisitController(
      VisitService visits,
      VisitStatusService statuses,
      VisitorSuggestionService suggestions,
      CurrentUserProvider currentUser) {
    this.visits = visits;
    this.statuses = statuses;
    this.suggestions = suggestions;
    this.currentUser = currentUser;
  }

  /** Planejamento (RN29): URGENT primeiro, depois as mais perto do vencimento. */
  @GetMapping("/visits/status")
  public PageResponse<PropertyVisitStatus> status(
      @RequestParam(required = false) UUID groupId,
      @RequestParam(required = false) VisitCycleCalculator.State state,
      @RequestParam(required = false) Boolean windowOpen,
      Pageable pageable) {
    var filtered =
        statuses.all(currentUser.get().associationId()).stream()
            .filter(s -> groupId == null || (s.group() != null && groupId.equals(s.group().id())))
            .filter(s -> state == null || s.state() == state)
            .filter(s -> windowOpen == null || s.windowOpen() == windowOpen)
            .toList();
    int from = (int) Math.min(pageable.getOffset(), filtered.size());
    int to = Math.min(from + pageable.getPageSize(), filtered.size());
    var content = filtered.subList(from, to);
    return PageResponse.of(new PageImpl<>(content, pageable, filtered.size()), content);
  }

  @GetMapping("/properties/{id}/visit-status")
  public PropertyVisitStatus propertyStatus(@PathVariable UUID id) {
    return statuses.forProperty(currentUser.get(), id);
  }

  @PostMapping("/visits/suggest-visitors")
  public VisitDtos.VisitorSuggestion suggest(@Valid @RequestBody VisitDtos.SuggestRequest body) {
    return suggestions.suggest(currentUser.get(), body);
  }

  @GetMapping("/visits")
  public PageResponse<VisitView> list(
      VisitFilter filter,
      @PageableDefault(sort = "plannedOn", direction = Sort.Direction.DESC) Pageable pageable) {
    return visits.list(currentUser.get(), filter, pageable);
  }

  @PostMapping("/visits")
  @ResponseStatus(HttpStatus.CREATED)
  public VisitView create(@Valid @RequestBody VisitDtos.VisitWrite body) {
    return visits.create(currentUser.get(), body);
  }

  @GetMapping("/visits/{id}")
  public VisitView get(@PathVariable UUID id) {
    return visits.get(currentUser.get(), id);
  }

  @PatchMapping("/visits/{id}")
  public VisitView patch(
      @PathVariable UUID id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = VisitDtos.VisitWrite.class)))
          @RequestBody
          JsonNode body) {
    return visits.patch(currentUser.get(), id, body);
  }

  @PostMapping("/visits/{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@PathVariable UUID id, @Valid @RequestBody VisitDtos.Reason body) {
    visits.cancel(currentUser.get(), id, body.reason());
  }

  @PutMapping("/visits/{id}/result")
  public VisitView result(@PathVariable UUID id, @Valid @RequestBody VisitDtos.ResultWrite body) {
    return visits.putResult(currentUser.get(), id, body);
  }

  @DeleteMapping("/visits/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    visits.delete(currentUser.get(), id);
  }
}
