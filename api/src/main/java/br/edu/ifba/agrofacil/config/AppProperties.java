// Configurações próprias do Agrofácil (bloco app.* do application.yml), lidas num lugar só.
package br.edu.ifba.agrofacil.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configurações do Agrofácil. A aplicação não sobe se faltar um valor obrigatório, como o {@code
 * JWT_SECRET}.
 *
 * @param timezone fuso usado nas datas sem hora e nas rotinas diárias (ex.: America/Bahia)
 * @param publicUrl endereço público do painel, usado nos links dos e-mails
 * @param minMobileVersion versão mínima do app aceita pela API (doc 2 §1.7.7)
 * @param termsVersion versão atual do termo de uso e do aviso de privacidade
 * @param secureCookies marca o cookie de refresh como {@code Secure} (ligado em produção)
 * @param logMailLinks escreve no log os links dos e-mails (só no desenvolvimento)
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @NotBlank String timezone,
    @NotBlank String publicUrl,
    @NotBlank String minMobileVersion,
    @NotBlank String termsVersion,
    boolean secureCookies,
    boolean logMailLinks,
    @Valid @NotNull Jwt jwt,
    @Valid @NotNull FarmerSession farmerSession,
    @Valid @NotNull CoordinatorSession coordinatorSession,
    @Valid @NotNull Storage storage,
    @Valid @NotNull Mail mail,
    @Valid @NotNull Push push,
    @Valid @NotNull Bootstrap bootstrap,
    @Valid @NotNull Documents documents) {

  /** Tamanho mínimo da chave HS256, em bytes. */
  private static final int MIN_JWT_SECRET_BYTES = 32;

  /** Fuso da associação, usado para saber que dia é "hoje". */
  public ZoneId zone() {
    return ZoneId.of(timezone);
  }

  /**
   * @param secret chave HS256 em base64, com 32 bytes ou mais; vem só de variável de ambiente
   * @param accessTtl validade do token de acesso
   */
  public record Jwt(@NotBlank String secret, @NotNull Duration accessTtl) {

    /** Chave HMAC do JWT. Falha ao subir se o segredo for curto demais. */
    public SecretKey secretKey() {
      byte[] bytes = Base64.getDecoder().decode(secret.trim());
      if (bytes.length < MIN_JWT_SECRET_BYTES) {
        throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 bytes (base64).");
      }
      return new SecretKeySpec(bytes, "HmacSHA256");
    }
  }

  /**
   * @param refreshIdleTtl o refresh do agricultor expira depois desse tempo sem uso (RN04)
   */
  public record FarmerSession(@NotNull Period refreshIdleTtl) {}

  /**
   * @param refreshIdleTtl a sessão web expira depois desse tempo sem uso (RN05)
   * @param refreshMaxTtl duração máxima da sessão web, mesmo em uso (RN05)
   */
  public record CoordinatorSession(
      @NotNull Duration refreshIdleTtl, @NotNull Period refreshMaxTtl) {}

  /**
   * @param dir pasta onde os anexos são gravados, fora de qualquer pasta pública
   */
  public record Storage(@NotBlank String dir) {}

  /**
   * @param from remetente dos e-mails da API
   */
  public record Mail(@NotBlank String from) {}

  /**
   * @param expoAccessToken token opcional do Expo para o envio de push
   * @param expoUrl endereço do serviço de push do Expo
   */
  public record Push(String expoAccessToken, @NotBlank String expoUrl) {}

  /**
   * @param coordinatorEmail e-mail que recebe o convite do primeiro coordenador (opcional)
   * @param associationId associação em que o primeiro coordenador é criado (ADR-14)
   */
  public record Bootstrap(String coordinatorEmail, @NotNull UUID associationId) {}

  /**
   * @param templateVersion versão atual dos modelos de documento e do roteiro da visita (doc 2
   *     §1.15.6); os documentos antigos guardam a versão com que foram gerados
   */
  public record Documents(@NotBlank String templateVersion) {}
}
