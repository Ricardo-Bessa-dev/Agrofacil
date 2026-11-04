// Aparelho na lista do coordenador (doc 1 §3.7.4).
package br.edu.ifba.agrofacil.user;

import java.time.Instant;
import java.util.UUID;

public record DeviceView(
    UUID id,
    String name,
    String platform,
    String appVersion,
    Instant createdAt,
    Instant lastSeenAt,
    Instant revokedAt) {

  public static DeviceView of(Device d) {
    return new DeviceView(
        d.getId(),
        d.getName(),
        d.getPlatform(),
        d.getAppVersion(),
        d.getCreatedAt(),
        d.getLastSeenAt(),
        d.getRevokedAt());
  }
}
