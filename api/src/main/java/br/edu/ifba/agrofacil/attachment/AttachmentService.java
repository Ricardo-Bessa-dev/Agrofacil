// Metadados e binários dos anexos: escopo, tipos aceitos, tamanho e conferência do hash (doc 1
// §3.6).
package br.edu.ifba.agrofacil.attachment;

import br.edu.ifba.agrofacil.announcement.AnnouncementRepository;
import br.edu.ifba.agrofacil.certification.CertificationRepository;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.property.PropertyService;
import br.edu.ifba.agrofacil.record.RecordService;
import br.edu.ifba.agrofacil.spg.visit.Visit;
import br.edu.ifba.agrofacil.spg.visit.VisitParticipantRepository;
import br.edu.ifba.agrofacil.spg.visit.VisitRepository;
import br.edu.ifba.agrofacil.spg.visit.VisitStatus;
import br.edu.ifba.agrofacil.user.FarmerProfile;
import br.edu.ifba.agrofacil.user.FarmerProfileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttachmentService {

  private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
  private static final DateTimeFormatter KEY_PREFIX =
      DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

  private final AttachmentRepository attachments;
  private final RecordService records;
  private final PropertyService properties;
  private final CertificationRepository certifications;
  private final AnnouncementRepository announcements;
  private final FarmerProfileRepository profiles;
  private final VisitRepository visits;
  private final VisitParticipantRepository visitParticipants;
  private final FileStorage storage;
  private final JdbcClient jdbc;
  private final Clock clock;
  private final Tika tika = new Tika();

  public AttachmentService(
      AttachmentRepository attachments,
      RecordService records,
      PropertyService properties,
      CertificationRepository certifications,
      AnnouncementRepository announcements,
      FarmerProfileRepository profiles,
      VisitRepository visits,
      VisitParticipantRepository visitParticipants,
      FileStorage storage,
      JdbcClient jdbc,
      Clock clock) {
    this.attachments = attachments;
    this.records = records;
    this.properties = properties;
    this.certifications = certifications;
    this.announcements = announcements;
    this.profiles = profiles;
    this.visits = visits;
    this.visitParticipants = visitParticipants;
    this.storage = storage;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Binário aberto para download, com os metadados para os cabeçalhos. */
  public record Content(Attachment attachment, InputStream stream) {}

  /**
   * Cria o metadado enviado pelo app (push). Repetir é seguro: um anexo existente do próprio
   * agricultor é devolvido como está.
   */
  @Transactional
  public Attachment upsertFromFarmer(CurrentUser farmer, UUID id, AttachmentWrite write) {
    var existing = attachments.findById(id);
    if (existing.isPresent()) {
      var a = existing.get();
      if (a.isDeleted() || !a.getCreatedBy().equals(farmer.id())) {
        throw ApiException.notFound();
      }
      return a;
    }
    var associationId = ensureOwnerWritable(farmer, write.ownerType(), write.ownerId());
    var attachment =
        new Attachment(
            id, associationId, write.ownerType(), write.ownerId(), write.kind(), farmer.id());
    attachment.describe(
        write.contentType(), write.sizeBytes(), write.sha256(), write.durationSeconds());
    var saved = attachments.saveAndFlush(attachment);
    afterChange(saved);
    return saved;
  }

  /**
   * Recebe o binário (PUT /files/{id}/content). Idempotente: se o arquivo já chegou com o mesmo
   * hash, não regrava.
   *
   * @throws ApiException ATTACHMENT_NOT_FOUND, FILE_TOO_LARGE, FILE_TYPE_NOT_ALLOWED ou
   *     CHECKSUM_MISMATCH
   */
  @Transactional
  public void uploadContent(CurrentUser user, UUID id, InputStream body) {
    var attachment =
        attachments
            .findById(id)
            .filter(a -> !a.isDeleted() && canWrite(user, a))
            .orElseThrow(() -> new ApiException(ErrorCode.ATTACHMENT_NOT_FOUND));
    byte[] bytes = readLimited(body, AttachmentRules.maxBytes(attachment.getKind()));
    String sha256 = sha256(bytes);
    if (attachment.isUploaded() && sha256.equals(attachment.getSha256())) {
      return;
    }
    if (attachment.getSha256() != null && !attachment.getSha256().equals(sha256)) {
      throw new ApiException(ErrorCode.CHECKSUM_MISMATCH);
    }
    store(attachment, bytes, sha256);
  }

  /** Abre o binário para quem pode ver o dono do anexo. */
  @Transactional(readOnly = true)
  public Content openContent(CurrentUser user, UUID id) {
    var attachment =
        attachments
            .findById(id)
            .filter(a -> !a.isDeleted() && canRead(user, a))
            .orElseThrow(() -> new ApiException(ErrorCode.ATTACHMENT_NOT_FOUND));
    if (!attachment.isUploaded()) {
      throw new ApiException(ErrorCode.ATTACHMENT_NOT_FOUND);
    }
    return new Content(attachment, storage.open(attachment.getStorageKey()));
  }

  /** Upload direto pelo painel (multipart), com metadado e binário juntos. */
  @Transactional
  public Attachment createByCoordinator(
      CurrentUser user,
      Attachment.OwnerType ownerType,
      UUID ownerId,
      Attachment.Kind kind,
      InputStream file) {
    var associationId = ensureOwnerWritable(user, ownerType, ownerId);
    byte[] bytes = readLimited(file, AttachmentRules.maxBytes(kind));
    var attachment =
        new Attachment(UUID.randomUUID(), associationId, ownerType, ownerId, kind, user.id());
    // Preenche tipo, hash e arquivo antes de gravar: a linha já nasce completa (content_type é
    // obrigatório) e vale a instância devolvida pelo save, que é a gerenciada pelo JPA.
    store(attachment, bytes, sha256(bytes));
    var saved = attachments.save(attachment);
    afterChange(saved);
    return saved;
  }

  /** Exclusão lógica; o arquivo fica até o prazo de guarda (RN15). Repetir é seguro. */
  @Transactional
  public Attachment delete(CurrentUser user, UUID id) {
    var attachment =
        attachments.findById(id).filter(a -> canWrite(user, a)).orElseThrow(ApiException::notFound);
    if (!attachment.isDeleted()) {
      attachment.markDeleted(clock.instant());
      attachments.saveAndFlush(attachment);
      afterChange(attachment);
    }
    return attachment;
  }

  @Transactional(readOnly = true)
  public List<AttachmentView> viewsFor(Attachment.OwnerType ownerType, UUID ownerId) {
    return attachments
        .findByOwnerTypeAndOwnerIdAndDeletedAtIsNullOrderByCreatedAt(ownerType, ownerId)
        .stream()
        .map(AttachmentView::of)
        .toList();
  }

  private void store(Attachment attachment, byte[] bytes, String sha256) {
    String detected = tika.detect(bytes);
    if (!AttachmentRules.isAllowed(attachment.getKind(), detected)) {
      throw new ApiException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
    }
    String key = KEY_PREFIX.format(clock.instant()) + "/" + attachment.getId();
    storage.save(key, bytes);
    attachment.describe(detected, (long) bytes.length, sha256, attachment.getDurationSeconds());
    attachment.markUploaded(key, bytes.length, sha256, clock.instant());
    log.info("Arquivo do anexo {} recebido ({} bytes)", attachment.getId(), bytes.length);
  }

  /** Foto de compra conta na completude: o registro dono é recalculado (doc 1 §2.5). */
  private void afterChange(Attachment attachment) {
    if (attachment.getOwnerType() == Attachment.OwnerType.RECORD) {
      records.refreshCompleteness(attachment.getOwnerId());
    }
  }

  /**
   * Confere se o usuário pode anexar ao dono e devolve a associação dele. O agricultor anexa a
   * registros e propriedades, e o responsável pelo registro, à visita (doc 2 §1.15.4); o
   * coordenador, também a certificações e comunicados.
   */
  private UUID ensureOwnerWritable(CurrentUser user, Attachment.OwnerType ownerType, UUID ownerId) {
    return switch (ownerType) {
      case RECORD -> records.findVisible(user, ownerId).getAssociationId();
      case PROPERTY -> properties.findVisible(user, ownerId).getAssociationId();
      case CERTIFICATION -> {
        if (user.isFarmer()) {
          throw ApiException.notFound();
        }
        var cert = certifications.findById(ownerId).filter(c -> !c.isDeleted());
        yield properties
            .findVisible(user, cert.orElseThrow(ApiException::notFound).getPropertyId())
            .getAssociationId();
      }
      case ANNOUNCEMENT -> {
        if (user.isFarmer()) {
          throw ApiException.notFound();
        }
        yield announcements
            .findByIdAndAssociationIdAndDeletedAtIsNull(ownerId, user.associationId())
            .orElseThrow(ApiException::notFound)
            .getAssociationId();
      }
      case DOCUMENT -> {
        if (user.isFarmer()) {
          throw ApiException.notFound();
        }
        yield documentAssociation(user, ownerId);
      }
      case VISIT -> {
        var visit =
            visits
                .findByIdAndAssociationIdAndDeletedAtIsNull(ownerId, user.associationId())
                .orElseThrow(ApiException::notFound);
        if (user.isFarmer() && !isRecorder(user, visit)) {
          throw ApiException.notFound();
        }
        yield visit.getAssociationId();
      }
      default ->
          throw ApiException.validation(
              "ownerType", "NOT_SUPPORTED", "Este tipo de anexo ainda não é aceito.");
    };
  }

  private boolean canWrite(CurrentUser user, Attachment a) {
    return user.isFarmer()
        ? a.getCreatedBy().equals(user.id())
        : a.getAssociationId().equals(user.associationId());
  }

  private boolean canRead(CurrentUser user, Attachment a) {
    if (!user.isFarmer()) {
      return a.getAssociationId().equals(user.associationId());
    }
    try {
      switch (a.getOwnerType()) {
        case RECORD -> records.findVisible(user, a.getOwnerId());
        case PROPERTY -> properties.findVisible(user, a.getOwnerId());
        case CERTIFICATION ->
            properties.findVisible(
                user,
                certifications
                    .findById(a.getOwnerId())
                    .orElseThrow(ApiException::notFound)
                    .getPropertyId());
        case ANNOUNCEMENT -> {
          return isAnnouncementVisibleToFarmer(user, a.getOwnerId());
        }
        case VISIT -> {
          return isVisitVisibleToFarmer(user, a.getOwnerId());
        }
        default -> {
          return false;
        }
      }
      return true;
    } catch (ApiException e) {
      return false;
    }
  }

  /** Documento gerado da associação (cópia assinada, RN37). */
  private UUID documentAssociation(CurrentUser user, UUID documentId) {
    Integer found =
        jdbc.sql("select 1 from generated_document where id = ? and association_id = ?")
            .params(documentId, user.associationId())
            .query(Integer.class)
            .optional()
            .orElseThrow(ApiException::notFound);
    return found == null ? null : user.associationId();
  }

  /** O responsável pelo registro é um participante agricultor ativo, escolhido na visita. */
  private boolean isRecorder(CurrentUser farmer, Visit visit) {
    return visit.getRecorderParticipantId() != null
        && visitParticipants.findByVisitIdAndDeletedAtIsNull(visit.getId()).stream()
            .anyMatch(p ->
                p.getId().equals(visit.getRecorderParticipantId())
                    && farmer.id().equals(p.getFarmerId()));
  }

  /** Participante da visita, ou dono da propriedade depois de a visita ser concluída (RN31). */
  private boolean isVisitVisibleToFarmer(CurrentUser farmer, UUID visitId) {
    var visit = visits.findById(visitId).filter(v -> !v.isDeleted()).orElse(null);
    if (visit == null) {
      return false;
    }
    boolean participant =
        visitParticipants.findByVisitIdAndDeletedAtIsNull(visitId).stream()
            .anyMatch(p -> farmer.id().equals(p.getFarmerId()));
    if (participant) {
      return true;
    }
    try {
      properties.findVisible(farmer, visit.getPropertyId());
      return !visit.isUnannounced() || visit.getStatus() == VisitStatus.DONE;
    } catch (ApiException e) {
      return false;
    }
  }

  /** Comunicado para todos ou para o grupo do agricultor (doc 1 §4.8). */
  private boolean isAnnouncementVisibleToFarmer(CurrentUser farmer, UUID announcementId) {
    var groupId = profiles.findById(farmer.id()).map(FarmerProfile::getGroupId).orElse(null);
    return announcements
        .findByIdAndAssociationIdAndDeletedAtIsNull(announcementId, farmer.associationId())
        .filter(n -> n.getGroupId() == null || Objects.equals(n.getGroupId(), groupId))
        .isPresent();
  }

  /** Lê o corpo até o limite; um byte a mais já é "grande demais". */
  private static byte[] readLimited(InputStream in, long max) {
    try (in) {
      byte[] bytes = in.readNBytes((int) max + 1);
      if (bytes.length > max) {
        throw new ApiException(ErrorCode.FILE_TOO_LARGE);
      }
      if (bytes.length == 0) {
        throw ApiException.validation("file", "REQUIRED", "O arquivo está vazio.");
      }
      return bytes;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
