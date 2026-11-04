// Gera, guarda, substitui, cancela e confere os documentos do SPG em PDF (RN37, RN38).
package br.edu.ifba.agrofacil.document;

import br.edu.ifba.agrofacil.association.AssociationService;
import br.edu.ifba.agrofacil.attachment.Attachment;
import br.edu.ifba.agrofacil.attachment.AttachmentService;
import br.edu.ifba.agrofacil.attachment.FileStorage;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.pdf.PdfRenderer;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.web.IdName;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import br.edu.ifba.agrofacil.config.AppProperties;
import br.edu.ifba.agrofacil.user.AppUser;
import br.edu.ifba.agrofacil.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * O PDF usa o template da versão atual ({@code app.documents.template-version}); os documentos
 * antigos continuam com a versão com que foram gerados.
 */
@Service
public class DocumentService {

  private static final DateTimeFormatter KEY_PREFIX =
      DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter GENERATED_AT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

  /** Tentativas de sortear um código ainda não usado (colisão é raríssima: 31^8 combinações). */
  private static final int CODE_ATTEMPTS = 5;

  private static final List<GeneratedDocument.Status> REPLACEABLE =
      List.of(GeneratedDocument.Status.GENERATED, GeneratedDocument.Status.SIGNED);

  private final GeneratedDocumentRepository documents;
  private final DocumentDataAssembler assembler;
  private final PdfRenderer pdf;
  private final FileStorage storage;
  private final AttachmentService attachments;
  private final AssociationService associations;
  private final UserRepository users;
  private final ObjectMapper mapper;
  private final AppProperties props;
  private final Clock clock;

  public DocumentService(
      GeneratedDocumentRepository documents,
      DocumentDataAssembler assembler,
      PdfRenderer pdf,
      FileStorage storage,
      AttachmentService attachments,
      AssociationService associations,
      UserRepository users,
      ObjectMapper mapper,
      AppProperties props,
      Clock clock) {
    this.documents = documents;
    this.assembler = assembler;
    this.pdf = pdf;
    this.storage = storage;
    this.attachments = attachments;
    this.associations = associations;
    this.users = users;
    this.mapper = mapper;
    this.props = props;
    this.clock = clock;
  }

  /**
   * Gera o PDF, guarda e marca o anterior do mesmo tipo e assunto como substituído (RN37).
   *
   * @throws ApiException 422 DOCUMENT_PRECONDITION_FAILED (RN38)
   */
  @Transactional
  public DocumentView generate(CurrentUser user, GenerateDocumentRequest req) {
    var data = assembler.assemble(user, req);
    var association = associations.get(user.associationId());
    String version = props.documents().templateVersion();
    var doc =
        new GeneratedDocument(
            UUID.randomUUID(),
            user.associationId(),
            req.kind(),
            version,
            data.title(),
            data.subject(),
            user.id(),
            clock.instant());
    String code = newCode();
    var model = new HashMap<String, Object>();
    model.put("d", data.body());
    model.put("kind", req.kind());
    model.put("opacName", association.getOpacName());
    model.put("associationName", association.getName());
    model.put("templateVersion", version);
    model.put("warnings", data.warnings());
    model.put("code", VerificationCodes.display(code));
    model.put("generatedAt", GENERATED_AT.format(clock.instant().atZone(props.zone())));
    byte[] bytes = pdf.render("document/" + version + "/" + req.kind().template(), model);
    String key = "documents/" + KEY_PREFIX.format(clock.instant()) + "/" + doc.getId() + ".pdf";
    storage.save(key, bytes);
    doc.store(
        code,
        sha256(bytes),
        key,
        bytes.length,
        mapper.writeValueAsString(data.body()),
        data.warnings());
    documents.save(doc);
    documents
        .findByAssociationIdAndKindAndStatusIn(user.associationId(), req.kind(), REPLACEABLE)
        .stream()
        .filter(old -> !old.getId().equals(doc.getId()) && old.subject().equals(doc.subject()))
        .forEach(old -> old.supersede(doc.getId()));
    documents.flush();
    return views(List.of(doc)).getFirst();
  }

  @Transactional(readOnly = true)
  public PageResponse<DocumentView> list(CurrentUser user, DocumentFilter filter, Pageable page) {
    var result = documents.findAll(specFor(user.associationId(), filter), page);
    return PageResponse.of(result, views(result.getContent()));
  }

  @Transactional(readOnly = true)
  public DocumentView get(CurrentUser user, UUID id) {
    return views(List.of(find(user, id))).getFirst();
  }

  /** PDF guardado e o nome do arquivo para download. */
  public record Content(String filename, InputStream stream) {}

  @Transactional(readOnly = true)
  public Content content(CurrentUser user, UUID id) {
    var doc = find(user, id);
    String filename =
        br.edu.ifba.agrofacil.report.ReportFile.slug(doc.getTitle())
            + "-"
            + doc.getVerificationCode().toLowerCase(java.util.Locale.ROOT)
            + ".pdf";
    return new Content(filename, storage.open(doc.getStorageKey()));
  }

  /** Recebe a cópia assinada (PDF ou foto) e marca o documento como assinado (RN37). */
  @Transactional
  public DocumentView signedCopy(CurrentUser user, UUID id, String contentType, InputStream file) {
    var doc = find(user, id);
    var kind =
        contentType != null && contentType.startsWith("image/")
            ? Attachment.Kind.PHOTO
            : Attachment.Kind.DOCUMENT;
    var attachment =
        attachments.createByCoordinator(
            user, Attachment.OwnerType.DOCUMENT, doc.getId(), kind, file);
    doc.markSigned(attachment.getId());
    return views(List.of(documents.saveAndFlush(doc))).getFirst();
  }

  @Transactional
  public void cancel(CurrentUser user, UUID id, String reason) {
    find(user, id).cancel(reason.trim());
  }

  /** Confere um papel impresso pelo código do rodapé. */
  @Transactional(readOnly = true)
  public DocumentView verify(CurrentUser user, String typedCode) {
    return documents
        .findByVerificationCodeAndAssociationId(
            VerificationCodes.normalize(typedCode), user.associationId())
        .map(d -> views(List.of(d)).getFirst())
        .orElseThrow(ApiException::notFound);
  }

  /** Documento da associação; 404 caso contrário. */
  @Transactional(readOnly = true)
  public GeneratedDocument find(CurrentUser user, UUID id) {
    return documents
        .findByIdAndAssociationId(id, user.associationId())
        .orElseThrow(ApiException::notFound);
  }

  private String newCode() {
    for (int i = 0; i < CODE_ATTEMPTS; i++) {
      String code = VerificationCodes.next();
      if (!documents.existsByVerificationCode(code)) {
        return code;
      }
    }
    throw new IllegalStateException("Não foi possível sortear um código de verificação livre.");
  }

  private List<DocumentView> views(List<GeneratedDocument> list) {
    Map<UUID, String> names =
        users
            .findByIdIn(list.stream().map(GeneratedDocument::getGeneratedBy).distinct().toList())
            .stream()
            .collect(Collectors.toMap(AppUser::getId, AppUser::getName));
    return list.stream()
        .map(d ->
            new DocumentView(
                d.getId(),
                d.getKind(),
                d.getTitle(),
                d.getTemplateVersion(),
                d.subject(),
                VerificationCodes.display(d.getVerificationCode()),
                d.getSha256(),
                d.getSizeBytes(),
                d.getStatus(),
                d.getWarnings(),
                d.getSignedAttachmentId(),
                d.getSupersededById(),
                d.getCancelReason(),
                new IdName(d.getGeneratedBy(), names.get(d.getGeneratedBy())),
                d.getGeneratedAt()))
        .toList();
  }

  private static Specification<GeneratedDocument> specFor(UUID associationId, DocumentFilter f) {
    return (root, query, cb) -> {
      var predicates = new ArrayList<Predicate>();
      predicates.add(cb.equal(root.get("associationId"), associationId));
      Map<String, Object> eq = new HashMap<>();
      eq.put("kind", f.kind());
      eq.put("status", f.status());
      eq.put("farmerId", f.farmerId());
      eq.put("propertyId", f.propertyId());
      eq.put("groupId", f.groupId());
      eq.put("visitId", f.visitId());
      eq.entrySet().stream()
          .filter(e -> Objects.nonNull(e.getValue()))
          .forEach(e -> predicates.add(cb.equal(root.get(e.getKey()), e.getValue())));
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
