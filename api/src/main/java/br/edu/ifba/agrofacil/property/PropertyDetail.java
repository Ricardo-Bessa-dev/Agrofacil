// Detalhe da propriedade no painel: a propriedade, as áreas, as pessoas e o histórico de
// certificações.
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.certification.CertificationView;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

public record PropertyDetail(
    @JsonUnwrapped PropertyView property,
    List<PlotView> plots,
    List<WorkerView> workers,
    List<CertificationView> certifications) {}
