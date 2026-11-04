// Endpoints dos documentos gerados do SPG (doc 1 §3.16); tudo é do coordenador.
package br.edu.ifba.agrofacil.document;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import br.edu.ifba.agrofacil.spg.visit.VisitDtos;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "SPG: documentos")
@RestController
public class DocumentController {

  private final DocumentService service;
  private final CurrentUserProvider currentUser;

  public DocumentController(DocumentService service, CurrentUserProvider currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @PostMapping("/documents")
  @ResponseStatus(HttpStatus.CREATED)
  public DocumentView generate(@Valid @RequestBody GenerateDocumentRequest body) {
    return service.generate(currentUser.get(), body);
  }

  @GetMapping("/documents")
  public PageResponse<DocumentView> list(
      DocumentFilter filter,
      @PageableDefault(sort = "generatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return service.list(currentUser.get(), filter, pageable);
  }

  /** Confere se um papel impresso corresponde a um documento gerado. */
  @GetMapping("/documents/verify/{code}")
  public DocumentView verify(@PathVariable String code) {
    return service.verify(currentUser.get(), code);
  }

  @GetMapping("/documents/{id}")
  public DocumentView get(@PathVariable UUID id) {
    return service.get(currentUser.get(), id);
  }

  @GetMapping("/documents/{id}/content")
  public ResponseEntity<InputStreamResource> content(@PathVariable UUID id) {
    var content = service.content(currentUser.get(), id);
    // Transmissão síncrona: o arquivo é copiado direto do disco para a resposta.
    var body = new InputStreamResource(content.stream());
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(content.filename()).build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .body(body);
  }

  @PostMapping(
      value = "/documents/{id}/signed-copy",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DocumentView signedCopy(@PathVariable UUID id, @RequestParam MultipartFile file) {
    try {
      return service.signedCopy(
          currentUser.get(), id, file.getContentType(), file.getInputStream());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @PostMapping("/documents/{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@PathVariable UUID id, @Valid @RequestBody VisitDtos.Reason body) {
    service.cancel(currentUser.get(), id, body.reason());
  }
}
