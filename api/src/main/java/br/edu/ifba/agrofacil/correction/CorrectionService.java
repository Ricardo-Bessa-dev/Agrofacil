// Pedidos de correção do coordenador; o agricultor é notificado (RN17).
package br.edu.ifba.agrofacil.correction;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import br.edu.ifba.agrofacil.notification.NotificationService;
import br.edu.ifba.agrofacil.notification.NotificationType;
import br.edu.ifba.agrofacil.record.RecordService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorrectionService {

  private final CorrectionRequestRepository corrections;
  private final RecordService records;
  private final NotificationService notifications;
  private final Clock clock;

  public CorrectionService(
      CorrectionRequestRepository corrections,
      RecordService records,
      NotificationService notifications,
      Clock clock) {
    this.corrections = corrections;
    this.records = records;
    this.notifications = notifications;
    this.clock = clock;
  }

  /** Abre um pedido num registro da associação e avisa o agricultor. */
  @Transactional
  public CorrectionRequestView create(CurrentUser user, UUID recordId, String message) {
    var record = records.findVisible(user, recordId);
    var request = corrections.save(new CorrectionRequest(recordId, message.trim(), user.id()));
    notifications.notify(
        List.of(record.getFarmerId()),
        NotificationType.CORRECTION_REQUESTED,
        "A associação pediu uma correção",
        message.trim(),
        Map.of("recordId", recordId.toString()));
    return CorrectionRequestView.of(request);
  }

  /** Resolve manualmente (o agricultor também resolve editando o registro). */
  @Transactional
  public void resolve(CurrentUser user, UUID id) {
    var request = corrections.findById(id).orElseThrow(ApiException::notFound);
    records.findVisible(user, request.getRecordId());
    request.resolve(user.id(), clock.instant());
  }

  @Transactional(readOnly = true)
  public PageResponse<CorrectionRequestView> list(
      CurrentUser user, CorrectionRequest.Status status, UUID farmerId, Pageable page) {
    return PageResponse.of(
        corrections.search(user.associationId(), status, farmerId, page),
        CorrectionRequestView::of);
  }
}
