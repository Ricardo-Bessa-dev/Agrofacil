// Gera dados de teste válidos e únicos (CPF, e-mail).
package br.edu.ifba.agrofacil.support;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestData {

  private static final AtomicInteger SEQ = new AtomicInteger();

  private TestData() {}

  /** CPF aleatório com dígitos verificadores corretos. */
  public static String cpf() {
    int[] d = new int[11];
    for (int i = 0; i < 9; i++) {
      d[i] = ThreadLocalRandom.current().nextInt(10);
    }
    d[9] = check(d, 9);
    d[10] = check(d, 10);
    var sb = new StringBuilder();
    for (int x : d) {
      sb.append(x);
    }
    return sb.toString();
  }

  public static String email() {
    return "coord" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@teste.org";
  }

  private static int check(int[] d, int length) {
    int sum = 0;
    for (int i = 0; i < length; i++) {
      sum += d[i] * (length + 1 - i);
    }
    int rest = (sum * 10) % 11;
    return rest == 10 ? 0 : rest;
  }
}
