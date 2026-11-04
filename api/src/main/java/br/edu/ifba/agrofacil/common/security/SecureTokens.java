// Geração de segredos aleatórios (tokens, códigos) e hash SHA-256 para guardá-los no banco.
package br.edu.ifba.agrofacil.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Só o hash vai para o banco; o valor em texto sai uma única vez na resposta ou no e-mail. */
public final class SecureTokens {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32; // 256 bits
  private static final int SIX_DIGITS = 1_000_000;

  private SecureTokens() {}

  /** Token opaco de 256 bits em base64url, sem preenchimento. */
  public static String newToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Código numérico de 6 dígitos (código de acesso e 2FA). */
  public static String newSixDigitCode() {
    return String.format("%06d", RANDOM.nextInt(SIX_DIGITS));
  }

  /** SHA-256 em hexadecimal (64 caracteres). */
  public static String sha256Hex(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponível", e);
    }
  }
}
