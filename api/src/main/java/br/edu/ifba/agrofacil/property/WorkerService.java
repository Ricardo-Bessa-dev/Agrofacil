// Pessoas vinculadas às propriedades: só o coordenador cadastra (doc 1 §3.17).
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.common.web.VersionCheck;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class WorkerService {

  private final WorkerRepository workers;
  private final PropertyService properties;
  private final JsonPatch jsonPatch;
  private final Clock clock;

  public WorkerService(
      WorkerRepository workers, PropertyService properties, JsonPatch jsonPatch, Clock clock) {
    this.workers = workers;
    this.properties = properties;
    this.jsonPatch = jsonPatch;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<WorkerView> list(CurrentUser user, UUID propertyId) {
    properties.findVisible(user, propertyId);
    return workers.findByPropertyIdAndDeletedAtIsNullOrderByName(propertyId).stream()
        .map(WorkerView::of)
        .toList();
  }

  @Transactional
  public WorkerView create(CurrentUser user, UUID propertyId, WorkerWrite write) {
    properties.findVisible(user, propertyId);
    var worker = new Worker(propertyId);
    worker.update(
        write.name().trim(), write.relationship(), write.active() == null || write.active());
    return WorkerView.of(workers.saveAndFlush(worker));
  }

  @Transactional
  public WorkerView patch(CurrentUser user, UUID id, JsonNode body) {
    var worker = findVisible(user, id);
    var write = jsonPatch.merge(WorkerView.of(worker).toWrite(), body, WorkerWrite.class);
    VersionCheck.ensure(body, write.version(), worker.getVersion());
    worker.update(
        write.name().trim(), write.relationship(), write.active() == null || write.active());
    return WorkerView.of(workers.saveAndFlush(worker));
  }

  @Transactional
  public void delete(CurrentUser user, UUID id) {
    findVisible(user, id).markDeleted(clock.instant());
  }

  private Worker findVisible(CurrentUser user, UUID id) {
    var worker = workers.findByIdAndDeletedAtIsNull(id).orElseThrow(ApiException::notFound);
    properties.findVisible(user, worker.getPropertyId());
    return worker;
  }
}
