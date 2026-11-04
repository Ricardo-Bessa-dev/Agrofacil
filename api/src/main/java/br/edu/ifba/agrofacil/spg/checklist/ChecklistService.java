// Roteiro efetivo da associação: o modelo padrão com os ajustes da própria associação (doc 1 §6.6).
package br.edu.ifba.agrofacil.spg.checklist;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.config.AppProperties;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Ajustar uma pergunta do modelo padrão cria uma cópia da associação com o mesmo {@code code}; a
 * cópia passa a valer no lugar da original. Assim o modelo padrão nunca muda.
 */
@Service
public class ChecklistService {

  private final VisitChecklistRepository repository;
  private final JsonPatch jsonPatch;
  private final AppProperties props;

  public ChecklistService(
      VisitChecklistRepository repository, JsonPatch jsonPatch, AppProperties props) {
    this.repository = repository;
    this.jsonPatch = jsonPatch;
    this.props = props;
  }

  /**
   * Perguntas que valem para a associação, em ordem.
   *
   * @param visitType PEER, VERIFICATION ou nulo (todas)
   */
  @Transactional(readOnly = true)
  public List<VisitChecklistItem> effective(
      UUID associationId, String visitType, boolean includeInactive) {
    var byCode = new LinkedHashMap<String, VisitChecklistItem>();
    var all = repository.findVisible(associationId);
    all.stream().filter(i -> i.getAssociationId() == null).forEach(i -> byCode.put(i.getCode(), i));
    all.stream().filter(i -> i.getAssociationId() != null).forEach(i -> byCode.put(i.getCode(), i));
    return byCode.values().stream()
        .filter(i -> includeInactive || i.isActive())
        .filter(i -> visitType == null || i.appliesTo(visitType))
        .sorted(
            Comparator.comparingInt(VisitChecklistItem::getSortOrder)
                .thenComparing(VisitChecklistItem::getCode))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ChecklistDtos.View> list(CurrentUser user, String type, boolean includeInactive) {
    return effective(user.associationId(), type, includeInactive).stream()
        .map(ChecklistDtos.View::of)
        .toList();
  }

  /** Pergunta nova da associação; o código não pode repetir um já existente. */
  @Transactional
  public ChecklistDtos.View create(CurrentUser user, ChecklistDtos.Write write) {
    if (write.code() == null) {
      throw ApiException.validation("code", "REQUIRED", "Informe o código da pergunta.");
    }
    boolean taken =
        effective(user.associationId(), null, true).stream()
            .anyMatch(i -> i.getCode().equals(write.code()));
    if (taken) {
      throw ApiException.validation("code", "DUPLICATE", "Já existe uma pergunta com este código.");
    }
    var item =
        new VisitChecklistItem(
            user.associationId(), write.code(), props.documents().templateVersion());
    apply(item, write);
    return ChecklistDtos.View.of(repository.saveAndFlush(item));
  }

  /** Ajusta uma pergunta; se for do modelo padrão, cria a cópia da associação. */
  @Transactional
  public ChecklistDtos.View patch(CurrentUser user, String code, JsonNode body) {
    var current =
        effective(user.associationId(), null, true).stream()
            .filter(i -> i.getCode().equals(code))
            .findFirst()
            .orElseThrow(ApiException::notFound);
    var write =
        jsonPatch.merge(ChecklistDtos.View.of(current).toWrite(), body, ChecklistDtos.Write.class);
    if (!code.equals(write.code())) {
      throw ApiException.validation("code", "NOT_EDITABLE", "O código da pergunta não muda.");
    }
    var item = current.getAssociationId() == null ? current.copyFor(user.associationId()) : current;
    apply(item, write);
    return ChecklistDtos.View.of(repository.saveAndFlush(item));
  }

  private static void apply(VisitChecklistItem item, ChecklistDtos.Write w) {
    item.update(
        w.appliesTo(),
        w.question().trim(),
        w.formalText().trim(),
        blankToNull(w.help()),
        blankToNull(w.legalReference()),
        w.sortOrder() == null ? 0 : w.sortOrder(),
        !Boolean.FALSE.equals(w.active()));
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
