// Endpoints dos relatórios em PDF e CSV (doc 1 §3.14).
package br.edu.ifba.agrofacil.report;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.record.RecordFilter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * O caderno de campo também serve ao agricultor, só da própria propriedade ({@code SecurityConfig}
 * libera a rota para qualquer usuário autenticado; o escopo é conferido no serviço).
 */
@Tag(name = "Relatórios")
@RestController
public class ReportController {

  private final ReportService reports;
  private final CurrentUserProvider currentUser;

  public ReportController(ReportService reports, CurrentUserProvider currentUser) {
    this.reports = reports;
    this.currentUser = currentUser;
  }

  @GetMapping("/reports/field-notebook")
  @ApiResponse(
      responseCode = "200",
      content = {
        @Content(
            mediaType = "application/pdf",
            schema = @Schema(type = "string", format = "binary")),
        @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary"))
      })
  public ResponseEntity<byte[]> fieldNotebook(
      @RequestParam UUID propertyId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String format) {
    return download(
        reports.fieldNotebook(currentUser.get(), propertyId, from, to, ReportFormat.parse(format)));
  }

  @GetMapping("/reports/members")
  public ResponseEntity<byte[]> members(@RequestParam(required = false) String format) {
    return download(reports.members(currentUser.get(), ReportFormat.parse(format)));
  }

  @GetMapping("/reports/certifications")
  public ResponseEntity<byte[]> certifications(@RequestParam(required = false) String format) {
    return download(reports.certificationsReport(currentUser.get(), ReportFormat.parse(format)));
  }

  /** Mesmos filtros de {@code GET /records}; só existe em CSV. */
  @GetMapping("/reports/records")
  public ResponseEntity<byte[]> records(
      RecordFilter filter, @RequestParam(required = false) String format) {
    if (format != null && ReportFormat.parse(format) != ReportFormat.CSV) {
      throw ApiException.validation("format", "INVALID", "Este relatório só sai em CSV.");
    }
    return download(reports.recordsCsv(currentUser.get(), filter));
  }

  private static ResponseEntity<byte[]> download(ReportFile file) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.filename()).build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .body(file.content());
  }
}
