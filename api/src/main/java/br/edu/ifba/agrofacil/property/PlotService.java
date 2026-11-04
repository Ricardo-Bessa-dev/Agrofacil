// Áreas das propriedades: só o coordenador cria, edita e exclui (RN08).
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.common.domain.AreaValue;
import br.edu.ifba.agrofacil.common.domain.MeasureRules;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.error.FieldErrorItem;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.common.web.VersionCheck;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class PlotService {

  private final PlotRepository plots;
  private final PropertyService properties;
  private final AssociationService associations;
  private final JsonPatch jsonPatch;
  private final Clock clock;

  public PlotService(
      PlotRepository plots,
      PropertyService properties,
      AssociationService associations,
      JsonPatch jsonPatch,
      Clock clock) {
    this.plots = plots;
    this.properties = properties;
    this.associations = associations;
    this.jsonPatch = jsonPatch;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<PlotView> list(CurrentUser user, UUID propertyId) {
    properties.findVisible(user, propertyId);
    return plots.findByPropertyIdAndDeletedAtIsNullOrderByName(propertyId).stream()
        .map(PlotView::of)
        .toList();
  }

  /** Cria uma área na propriedade. O nome não pode repetir na mesma propriedade. */
  @Transactional
  public PlotView create(CurrentUser user, UUID propertyId, PlotWrite write) {
    properties.findVisible(user, propertyId);
    var plot = new Plot(propertyId, null, null, write.status());
    apply(user, plot, write);
    return PlotView.of(plots.saveAndFlush(plot));
  }

  @Transactional
  public PlotView patch(CurrentUser user, UUID id, JsonNode body) {
    var plot = findVisible(user, id);
    var write = jsonPatch.merge(PlotView.of(plot).toWrite(), body, PlotWrite.class);
    VersionCheck.ensure(body, write.version(), plot.getVersion());
    apply(user, plot, write);
    return PlotView.of(plots.saveAndFlush(plot));
  }

  /**
   * Exclusão lógica. A propriedade precisa manter ao menos uma área ativa (RN08).
   *
   * @throws ApiException 409 LAST_PLOT
   */
  @Transactional
  public void delete(CurrentUser user, UUID id) {
    var plot = findVisible(user, id);
    if (plots.countByPropertyIdAndDeletedAtIsNull(plot.getPropertyId()) <= 1) {
      throw new ApiException(ErrorCode.LAST_PLOT);
    }
    plot.markDeleted(clock.instant());
  }

  /** Área visível ao usuário (a propriedade dela precisa estar no escopo). */
  @Transactional(readOnly = true)
  public Plot findVisible(CurrentUser user, UUID id) {
    var plot = plots.findByIdAndDeletedAtIsNull(id).orElseThrow(ApiException::notFound);
    properties.findVisible(user, plot.getPropertyId());
    return plot;
  }

  private void apply(CurrentUser user, Plot plot, PlotWrite write) {
    var errors = new ArrayList<FieldErrorItem>();
    MeasureRules.checkArea("area", write.area()).ifPresent(errors::add);
    if (write.conversionEndsOn() != null && write.status() != PlotStatus.IN_CONVERSION) {
      errors.add(
          new FieldErrorItem(
              "conversionEndsOn",
              "ONLY_IN_CONVERSION",
              "O fim da conversão só vale para área em conversão."));
    }
    if (!errors.isEmpty()) {
      throw ApiException.validation(errors);
    }
    String name = write.name().trim();
    if (plots.nameInUse(plot.getPropertyId(), name, plot.getId())) {
      throw new ApiException(ErrorCode.PLOT_NAME_IN_USE);
    }
    var tarefaM2 = associations.get(user.associationId()).getTarefaM2();
    plot.update(
        name,
        AreaValue.from(write.area(), tarefaM2),
        write.status(),
        write.conversionEndsOn(),
        write.notes());
  }
}
