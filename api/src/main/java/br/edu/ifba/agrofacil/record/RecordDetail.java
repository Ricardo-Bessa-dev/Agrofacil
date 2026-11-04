// Detalhe de um registro no painel: o registro, os anexos e os pedidos de correção (doc 1 §3.9.2).
package br.edu.ifba.agrofacil.record;

import br.edu.ifba.agrofacil.attachment.AttachmentView;
import br.edu.ifba.agrofacil.correction.CorrectionRequestView;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

public record RecordDetail(
    @JsonUnwrapped RecordView record,
    List<AttachmentView> attachments,
    List<CorrectionRequestView> correctionRequests) {}
