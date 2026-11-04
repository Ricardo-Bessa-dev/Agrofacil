// Associação no formato do contrato (doc 1 §3.7.1).
package br.edu.ifba.agrofacil.association;

import br.edu.ifba.agrofacil.common.domain.Decimals;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AssociationView(
    UUID id,
    String name,
    String cnpj,
    String email,
    String phone,
    String whatsapp,
    String opacName,
    BigDecimal tarefaM2,
    List<Integer> alertDays,
    int accessCodeTtlHours,
    int retentionYears,
    int visitWindowDays,
    int peerVisitsPerYear,
    int visitorsPerVisit,
    int visitReminderDays,
    boolean verificationSameGroupAllowed,
    Long version) {

  public static AssociationView of(Association a) {
    return new AssociationView(
        a.getId(),
        a.getName(),
        a.getCnpj(),
        a.getEmail(),
        a.getPhone(),
        a.getWhatsapp(),
        a.getOpacName(),
        Decimals.round(a.getTarefaM2()),
        List.copyOf(a.getAlertDays()),
        a.getAccessCodeTtlHours(),
        a.getRetentionYears(),
        a.getVisitWindowDays(),
        a.getPeerVisitsPerYear(),
        a.getVisitorsPerVisit(),
        a.getVisitReminderDays(),
        a.isVerificationSameGroupAllowed(),
        a.getVersion());
  }

  /** Converte para o formato de escrita, usado como base do PATCH. */
  public AssociationWrite toWrite() {
    return new AssociationWrite(
        name,
        cnpj,
        email,
        phone,
        whatsapp,
        opacName,
        tarefaM2,
        alertDays,
        accessCodeTtlHours,
        retentionYears,
        visitWindowDays,
        peerVisitsPerYear,
        visitorsPerVisit,
        visitReminderDays,
        verificationSameGroupAllowed,
        version);
  }
}
