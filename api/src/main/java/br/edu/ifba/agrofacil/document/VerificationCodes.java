// Gera o código de verificação impresso no rodapé dos documentos, ex.: K7QM-4TZP (RN37).
package br.edu.ifba.agrofacil.document;

import java.security.SecureRandom;

/** Sem 0/O e 1/I/L, para ninguém confundir ao digitar o código lido no papel. */
public final class VerificationCodes {

  static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
  static final int LENGTH = 8;
  private static final int GROUP = 4;
  private static final SecureRandom RANDOM = new SecureRandom();

  private VerificationCodes() {}

  /** Código novo, sem hífen (como é gravado). */
  public static String next() {
    var sb = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }

  /** "K7QM4TZP" → "K7QM-4TZP". */
  public static String display(String code) {
    return code.substring(0, GROUP) + "-" + code.substring(GROUP);
  }

  /** Aceita o código digitado com hífen, espaços ou minúsculas. */
  public static String normalize(String typed) {
    return typed == null
        ? ""
        : typed.replaceAll("[^0-9A-Za-z]", "").toUpperCase(java.util.Locale.ROOT);
  }
}
