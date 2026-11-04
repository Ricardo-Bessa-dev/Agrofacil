// Tipos e tamanhos aceitos por tipo de anexo (doc 1 §3.6). Função pura.
package br.edu.ifba.agrofacil.attachment;

import java.util.Map;
import java.util.Set;

public final class AttachmentRules {

  public static final int MAX_AUDIO_SECONDS = 180;
  private static final long MB = 1024L * 1024L;

  /** Limite de tamanho por tipo, em bytes. */
  private static final Map<Attachment.Kind, Long> MAX_BYTES =
      Map.of(
          Attachment.Kind.PHOTO, 5 * MB,
          Attachment.Kind.AUDIO, 10 * MB,
          Attachment.Kind.DOCUMENT, 10 * MB);

  /**
   * Tipos detectados pelo conteúdo (Tika). O áudio .m4a do app às vezes é detectado como o
   * contêiner MP4 genérico, por isso {@code video/mp4} é aceito para áudio.
   */
  private static final Map<Attachment.Kind, Set<String>> ALLOWED_TYPES =
      Map.of(
          Attachment.Kind.PHOTO, Set.of("image/jpeg", "image/png", "image/webp"),
          Attachment.Kind.AUDIO,
              Set.of("audio/mp4", "audio/aac", "audio/x-aac", "audio/x-m4a", "video/mp4"),
          Attachment.Kind.DOCUMENT, Set.of("application/pdf"));

  private AttachmentRules() {}

  public static long maxBytes(Attachment.Kind kind) {
    return MAX_BYTES.get(kind);
  }

  public static boolean isAllowed(Attachment.Kind kind, String detectedType) {
    return detectedType != null && ALLOWED_TYPES.get(kind).contains(detectedType);
  }

  /** Tipo de anexo adequado ao arquivo enviado pelo painel, a partir do tipo detectado. */
  public static Attachment.Kind kindFor(String detectedType) {
    return ALLOWED_TYPES.entrySet().stream()
        .filter(e -> e.getValue().contains(detectedType))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }
}
