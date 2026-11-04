// Dados do caderno de campo já formatados para o template do PDF (doc 2 §1.14).
package br.edu.ifba.agrofacil.report;

import java.util.List;

/**
 * Tudo em texto pronto: o template só exibe, sem regra nenhuma.
 *
 * @param certification nulo se a propriedade não tem certificação
 */
public record FieldNotebookData(
    String associationName,
    String opacName,
    String farmerName,
    String propertyName,
    String municipality,
    String totalArea,
    String period,
    List<PlotLine> plots,
    CertificationLine certification,
    List<PlantingBlock> plantings,
    List<Line> otherApplications,
    List<Line> purchases,
    List<Line> outflows,
    String generatedAt) {

  public record PlotLine(String name, String status, String area) {}

  public record CertificationLine(
      String number, String issuedOn, String expiresOn, String status) {}

  /** Uma linha de registro: data, número, área, item e quantidade/tamanho. */
  public record Line(String date, String number, String plot, String item, String detail) {}

  public record PlantingBlock(Line planting, List<Line> applications, List<Line> harvests) {}
}
