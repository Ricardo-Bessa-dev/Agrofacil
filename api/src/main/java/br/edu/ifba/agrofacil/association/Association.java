// Associação (Núcleo do OPAC) e suas configurações: tarefa, alertas, prazos e regras do SPG.
package br.edu.ifba.agrofacil.association;

import br.edu.ifba.agrofacil.common.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Os padrões estão no banco (V1); a associação é criada pela migração V5 com nome provisório. */
@Entity
@Table(name = "association")
public class Association extends BaseEntity {

  private String name;

  @JdbcTypeCode(SqlTypes.CHAR) // coluna char(n)
  private String cnpj;

  private String email;
  private String phone;
  private String whatsapp; // E.164, ex.: +5574999998888
  private String opacName;
  private BigDecimal tarefaM2; // tamanho da tarefa em m² (padrão 4.356, ADR-12)

  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<Integer> alertDays = new ArrayList<>(); // dias antes do vencimento (RN20)

  private int accessCodeTtlHours;
  private int retentionYears;
  private int visitWindowDays;
  private int peerVisitsPerYear;
  private int visitorsPerVisit;
  private int visitReminderDays;
  private boolean verificationSameGroupAllowed;

  protected Association() {}

  /** Aplica os dados editáveis vindos do painel (já validados). */
  public void update(AssociationWrite w) {
    name = w.name();
    cnpj = w.cnpj();
    email = w.email();
    phone = w.phone();
    whatsapp = w.whatsapp();
    opacName = w.opacName();
    tarefaM2 = w.tarefaM2();
    alertDays = new ArrayList<>(w.alertDays());
    accessCodeTtlHours = w.accessCodeTtlHours();
    retentionYears = w.retentionYears();
    visitWindowDays = w.visitWindowDays();
    peerVisitsPerYear = w.peerVisitsPerYear();
    visitorsPerVisit = w.visitorsPerVisit();
    visitReminderDays = w.visitReminderDays();
    verificationSameGroupAllowed = w.verificationSameGroupAllowed();
  }

  public String getName() {
    return name;
  }

  public String getCnpj() {
    return cnpj;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getWhatsapp() {
    return whatsapp;
  }

  public String getOpacName() {
    return opacName;
  }

  public BigDecimal getTarefaM2() {
    return tarefaM2;
  }

  public List<Integer> getAlertDays() {
    return alertDays;
  }

  public int getAccessCodeTtlHours() {
    return accessCodeTtlHours;
  }

  public int getRetentionYears() {
    return retentionYears;
  }

  public int getVisitWindowDays() {
    return visitWindowDays;
  }

  public int getPeerVisitsPerYear() {
    return peerVisitsPerYear;
  }

  public int getVisitorsPerVisit() {
    return visitorsPerVisit;
  }

  public int getVisitReminderDays() {
    return visitReminderDays;
  }

  public boolean isVerificationSameGroupAllowed() {
    return verificationSameGroupAllowed;
  }
}
