// Normaliza, valida e mascara CPFs (RN02).
package br.edu.ifba.agrofacil.common.domain;

/** O CPF é guardado só com dígitos; nos logs e listas aparece mascarado. */
public final class Cpf {

  private static final int LENGTH = 11;

  private Cpf() {}

  /** Tira pontos, traço e espaços. Devolve {@code null} se a entrada for nula. */
  public static String normalize(String raw) {
    return raw == null ? null : raw.replaceAll("\\D", "");
  }

  /** Confere tamanho, repetição (111.111.111-11) e os dois dígitos verificadores. */
  public static boolean isValid(String raw) {
    String cpf = normalize(raw);
    if (cpf == null || cpf.length() != LENGTH || cpf.chars().distinct().count() == 1) {
      return false;
    }
    return checkDigit(cpf, 9) == cpf.charAt(9) - '0' && checkDigit(cpf, 10) == cpf.charAt(10) - '0';
  }

  /** Máscara do contrato: {@code ***.456.789-**} (doc 1 §3.7.4 e §5.4). */
  public static String mask(String raw) {
    String cpf = normalize(raw);
    if (cpf == null || cpf.length() != LENGTH) {
      return "***.***.***-**";
    }
    return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
  }

  /** Dígito verificador da posição {@code length} (9 ou 10), pelo módulo 11. */
  private static int checkDigit(String cpf, int length) {
    int sum = 0;
    for (int i = 0; i < length; i++) {
      sum += (cpf.charAt(i) - '0') * (length + 1 - i);
    }
    int rest = (sum * 10) % 11;
    return rest == 10 ? 0 : rest;
  }
}
