// Envio e download dos binários dos anexos, e upload direto pelo painel (doc 1 §3.6).
package br.edu.ifba.agrofacil.attachment;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "Arquivos")
@RestController
public class FilesController {

  private static final long CACHE_SECONDS = 86_400; // um dia, só no aparelho (private)

  private final AttachmentService service;
  private final CurrentUserProvider currentUser;

  public FilesController(AttachmentService service, CurrentUserProvider currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @Operation(summary = "Envia o binário de um anexo já sincronizado (idempotente)")
  @PutMapping(value = "/files/{id}/content", consumes = MediaType.ALL_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void upload(@PathVariable UUID id, HttpServletRequest request) throws IOException {
    service.uploadContent(currentUser.get(), id, request.getInputStream());
  }

  @Operation(summary = "Baixa o binário (no web, use fetch + URL.createObjectURL)")
  @GetMapping("/files/{id}/content")
  public ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id) {
    var content = service.openContent(currentUser.get(), id);
    var attachment = content.attachment();
    StreamingResponseBody body =
        out -> {
          try (var in = content.stream()) {
            in.transferTo(out);
          }
        };
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(attachment.getContentType()))
        .cacheControl(
            CacheControl.maxAge(java.time.Duration.ofSeconds(CACHE_SECONDS)).cachePrivate())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(attachment.getId().toString()).build().toString())
        .body(body);
  }

  @Operation(summary = "Painel: envia um arquivo e cria o anexo de uma vez")
  @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public AttachmentView create(
      @RequestParam MultipartFile file,
      @RequestParam Attachment.OwnerType ownerType,
      @RequestParam UUID ownerId,
      @RequestParam Attachment.Kind kind)
      throws IOException {
    return AttachmentView.of(
        service.createByCoordinator(
            currentUser.get(), ownerType, ownerId, kind, file.getInputStream()));
  }

  @DeleteMapping("/attachments/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(currentUser.get(), id);
  }
}
