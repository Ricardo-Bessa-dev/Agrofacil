// Endpoints do painel para propriedades, áreas e pessoas vinculadas (doc 1 §3.8).
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.common.web.PageResponse;
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

/** O agricultor não usa estas rotas: ele escreve pelo push e recebe pelo pull. */
@Tag(name = "Propriedades")
@RestController
public class PropertyController {

  private final PropertyService properties;
  private final PlotService plots;
  private final WorkerService workers;
  private final CurrentUserProvider currentUser;

  public PropertyController(
      PropertyService properties,
      PlotService plots,
      WorkerService workers,
      CurrentUserProvider currentUser) {
    this.properties = properties;
    this.plots = plots;
    this.workers = workers;
    this.currentUser = currentUser;
  }

  @GetMapping("/properties")
  public PageResponse<PropertyView> list(
      PropertyFilter filter, @PageableDefault(sort = "name") Pageable pageable) {
    return properties.list(currentUser.get(), filter, pageable);
  }

  @GetMapping("/properties/map")
  public List<PropertyMapItem> map() {
    return properties.map(currentUser.get());
  }

  @PostMapping("/properties")
  @ResponseStatus(HttpStatus.CREATED)
  public PropertyView create(@Valid @RequestBody PropertyWrite body) {
    return properties.create(currentUser.get(), body);
  }

  @GetMapping("/properties/{id}")
  public PropertyDetail get(@PathVariable UUID id) {
    return properties.detail(currentUser.get(), id);
  }

  @PatchMapping("/properties/{id}")
  public PropertyView patch(
      @PathVariable UUID id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = PropertyWrite.class)))
          @RequestBody
          JsonNode body) {
    return properties.patch(currentUser.get(), id, body);
  }

  @DeleteMapping("/properties/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    properties.delete(currentUser.get(), id);
  }

  @GetMapping("/properties/{id}/plots")
  public List<PlotView> listPlots(@PathVariable UUID id) {
    return plots.list(currentUser.get(), id);
  }

  @PostMapping("/properties/{id}/plots")
  @ResponseStatus(HttpStatus.CREATED)
  public PlotView createPlot(@PathVariable UUID id, @Valid @RequestBody PlotWrite body) {
    return plots.create(currentUser.get(), id, body);
  }

  @PatchMapping("/plots/{id}")
  public PlotView patchPlot(
      @PathVariable UUID id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = PlotWrite.class)))
          @RequestBody
          JsonNode body) {
    return plots.patch(currentUser.get(), id, body);
  }

  @DeleteMapping("/plots/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePlot(@PathVariable UUID id) {
    plots.delete(currentUser.get(), id);
  }

  @GetMapping("/properties/{id}/workers")
  public List<WorkerView> listWorkers(@PathVariable UUID id) {
    return workers.list(currentUser.get(), id);
  }

  @PostMapping("/properties/{id}/workers")
  @ResponseStatus(HttpStatus.CREATED)
  public WorkerView createWorker(@PathVariable UUID id, @Valid @RequestBody WorkerWrite body) {
    return workers.create(currentUser.get(), id, body);
  }

  @PatchMapping("/workers/{id}")
  public WorkerView patchWorker(
      @PathVariable UUID id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content = @Content(schema = @Schema(implementation = WorkerWrite.class)))
          @RequestBody
          JsonNode body) {
    return workers.patch(currentUser.get(), id, body);
  }

  @DeleteMapping("/workers/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteWorker(@PathVariable UUID id) {
    workers.delete(currentUser.get(), id);
  }
}
