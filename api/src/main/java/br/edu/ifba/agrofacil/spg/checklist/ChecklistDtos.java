// Formatos do roteiro da visita no contrato (doc 1 §3.15).
package br.edu.ifba.agrofacil.spg.checklist;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ChecklistDtos {

  private ChecklistDtos() {}

  /**
   * @param code só na criação, ex.: {@code INPUTS_ALLOWED}; não muda depois
   * @param question texto do app (falado pelo 🔊)
   * @param formalText texto do PDF
   */
  public record Write(
      @Pattern(regexp = "[A-Z][A-Z0-9_]{2,59}") String code,
      @NotNull VisitChecklistItem.AppliesTo appliesTo,
      @NotBlank @Size(max = 300) String question,
      @NotBlank @Size(max = 500) String formalText,
      @Size(max = 1000) String help,
      @Size(max = 200) String legalReference,
      @Min(0) @Max(9999) Integer sortOrder,
      Boolean active) {}

  public record View(
      String code,
      VisitChecklistItem.AppliesTo appliesTo,
      String question,
      String formalText,
      String help,
      String legalReference,
      int sortOrder,
      String templateVersion,
      boolean active) {

    public static View of(VisitChecklistItem i) {
      return new View(
          i.getCode(),
          i.getAppliesTo(),
          i.getQuestion(),
          i.getFormalText(),
          i.getHelp(),
          i.getLegalReference(),
          i.getSortOrder(),
          i.getTemplateVersion(),
          i.isActive());
    }

    public Write toWrite() {
      return new Write(
          code, appliesTo, question, formalText, help, legalReference, sortOrder, active);
    }
  }
}
