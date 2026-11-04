// Tipos de documento do SPG gerados em PDF (D1 a D8, doc 1 §6.8).
package br.edu.ifba.agrofacil.document;

import java.util.Locale;

/** Cada tipo tem um template com o mesmo nome em kebab-case, ex.: {@code visit-form.html}. */
public enum DocumentKind {
  MEMBERSHIP_REQUEST(
      "Requerimento de adesão ao SPG e termo de compromisso", "IN MAPA 19/2009, arts. 71, 72 e 75"),
  EVALUATION_REQUEST("Solicitação de avaliação da conformidade", "IN MAPA 19/2009, art. 75"),
  VISIT_PLAN("Agenda de visitas do grupo", "IN MAPA 19/2009, arts. 73 e 81"),
  VISIT_FORM("Roteiro de visita", "IN MAPA 19/2009, art. 79 §1º, I"),
  PEER_VISIT_RECORD("Registro da visita de pares", "IN MAPA 19/2009, art. 81"),
  VERIFICATION_REPORT("Relatório da visita de verificação", "IN MAPA 19/2009, art. 79 §1º, III"),
  CONFORMITY_DECISION(
      "Documento de Aprovação ou Renovação da Conformidade Orgânica", "IN MAPA 19/2009, art. 84"),
  CERTIFICATE(
      "Certificado de Conformidade Orgânica (modelo para o OPAC)", "IN MAPA 19/2009, art. 90");

  private final String title;
  private final String legalBasis;

  DocumentKind(String title, String legalBasis) {
    this.title = title;
    this.legalBasis = legalBasis;
  }

  public String title() {
    return title;
  }

  public String legalBasis() {
    return legalBasis;
  }

  /** Nome do template, ex.: {@code conformity-decision}. */
  public String template() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
