// Dados do aparelho enviados pelo app na ativação (doc 1 §3.3.1).
package br.edu.ifba.agrofacil.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @param id gerado na instalação do app e guardado no SecureStore
 * @param platform {@code ANDROID} ou {@code IOS}
 */
public record DeviceInfo(
    @NotNull UUID id,
    @Size(max = 80) String name,
    @NotBlank @Pattern(regexp = "ANDROID|IOS") String platform,
    @NotBlank @Size(max = 20) String appVersion) {}
