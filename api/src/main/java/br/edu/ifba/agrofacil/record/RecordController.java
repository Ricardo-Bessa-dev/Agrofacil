// Endpoints do painel para registros de manejo (doc 1 §3.9.2).
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Registros")
@RestController
@RequestMapping("/records")
public class RecordController {

  private final RecordService records;
  private final RecordQueryService queries;
  private final CurrentUserProvider currentUser;

  public RecordController(
      RecordService records, RecordQueryService queries, CurrentUserProvider currentUser) {
    this.records = records;
    this.queries = queries;
    this.currentUser = currentUser;
  }

  @GetMapping
  public PageResponse<RecordListItem> list(
      RecordFilter filter,
      @PageableDefault(sort = "performedOn", direction = Sort.Direction.DESC) Pageable pageable) {
    return queries.list(currentUser.get(), filter, pageable);
  }

  @GetMapping("/{id}")
  public RecordDetail get(@PathVariable UUID id) {
    return queries.detail(currentUser.get(), id);
  }

  @Operation(summary = "Registra em nome do agricultor; ele é notificado (RN16)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RecordView create(@Valid @RequestBody RecordWrite body) {
    var user = currentUser.get();
    var saved = records.upsert(user, UUID.randomUUID(), body, RecordSource.WEB);
    return records.views(List.of(saved)).getFirst();
  }

  @Operation(summary = "Edição completa; envie a version atual")
  @PutMapping("/{id}")
  public RecordView update(@PathVariable UUID id, @Valid @RequestBody RecordWrite body) {
    var saved = records.update(currentUser.get(), id, body);
    return records.views(List.of(saved)).getFirst();
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    records.delete(currentUser.get(), id);
  }

  @GetMapping("/{id}/history")
  public List<RecordHistoryEntry> history(@PathVariable UUID id) {
    return queries.history(currentUser.get(), id);
  }
}
