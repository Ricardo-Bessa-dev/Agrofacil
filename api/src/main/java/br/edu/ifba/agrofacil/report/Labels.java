// Nomes em português dos valores do contrato, para relatórios e documentos impressos.
package br.edu.ifba.agrofacil.report;

import br.edu.ifba.agrofacil.catalog.MeasurementUnit;
import br.edu.ifba.agrofacil.certification.CertificationStatus;
import br.edu.ifba.agrofacil.common.domain.Area;
import br.edu.ifba.agrofacil.common.domain.BrFormat;
import br.edu.ifba.agrofacil.common.domain.Quantity;
import br.edu.ifba.agrofacil.property.PlotStatus;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordType;
import java.math.BigDecimal;
import java.util.Map;

/** Linguagem simples, igual à do app (doc 3). */
public final class Labels {

  private static final Map<String, String> AREA_UNITS =
      Map.of("HECTARE", "ha", "SQUARE_METER", "m²", "TAREFA", "tarefa(s)");

  private Labels() {}

  public static String recordType(RecordType type) {
    return switch (type) {
      case PLANTING -> "Plantio";
      case INPUT_APPLICATION -> "Aplicação de insumo";
      case HARVEST -> "Colheita";
      case INPUT_PURCHASE -> "Compra de insumo";
      case OUTFLOW -> "Saída / venda";
    };
  }

  /** Sem certificação = "Sem certificação". */
  public static String certificationStatus(CertificationStatus status) {
    if (status == null) {
      return "Sem certificação";
    }
    return switch (status) {
      case VALID -> "Em dia";
      case EXPIRING -> "Vence em breve";
      case EXPIRED -> "Vencida";
      case SUSPENDED -> "Suspensa";
      case CANCELLED -> "Cancelada";
    };
  }

  public static String plotStatus(PlotStatus status) {
    return switch (status) {
      case ORGANIC -> "Orgânica";
      case IN_CONVERSION -> "Em conversão";
      case NON_ORGANIC -> "Não orgânica";
      case NOT_DEFINED -> "A definir";
    };
  }

  /** Ex.: "3 tarefa(s)"; vazio sem área. */
  public static String area(Area area) {
    if (area == null) {
      return "";
    }
    return BrFormat.decimal(area.value()) + " " + AREA_UNITS.getOrDefault(area.unit(), area.unit());
  }

  /**
   * Ex.: "30 sacos", "1 saco"; usa o nome da unidade do catálogo.
   *
   * @param units unidades pelo código
   */
  public static String quantity(Quantity q, Map<String, MeasurementUnit> units) {
    if (q == null) {
      return "";
    }
    var unit = units.get(q.unit());
    String name =
        unit == null
            ? q.unit()
            : q.value().compareTo(BigDecimal.ONE) == 0
                ? unit.getNameSingular()
                : unit.getNamePlural();
    return BrFormat.decimal(q.value()) + " " + name;
  }
}
