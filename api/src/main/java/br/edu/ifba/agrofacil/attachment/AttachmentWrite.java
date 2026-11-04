// Metadados de um anexo enviados pelo push do app (doc 1 §3.6).
package br.edu.ifba.agrofacil.attachment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

/**
 * @param sha256 calculado pelo app; o servidor confere quando o binário chega
 * @param durationSeconds só para áudio (até 3 min)
 */
public record AttachmentWrite(
    UUID id,
    @NotNull Attachment.OwnerType ownerType,
    @NotNull UUID ownerId,
    @NotNull Attachment.Kind kind,
    @NotBlank String contentType,
    @Positive Long sizeBytes,
    @Pattern(regexp = "[0-9a-f]{64}") String sha256,
    @PositiveOrZero @Max(AttachmentRules.MAX_AUDIO_SECONDS) Integer durationSeconds) {}
