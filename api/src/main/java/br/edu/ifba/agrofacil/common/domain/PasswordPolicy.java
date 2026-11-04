// Regra de senha do coordenador (RN05): 10+ caracteres e fora da lista de senhas comuns.
package br.edu.ifba.agrofacil.common.domain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** A lista fica em {@code common-passwords.txt}, uma senha por linha. */
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 10;
  private static final Set<String> COMMON = loadCommonPasswords();

  private PasswordPolicy() {}

  public static boolean isStrong(String password) {
    return password != null
        && password.length() >= MIN_LENGTH
        && !COMMON.contains(password.toLowerCase(Locale.ROOT));
  }

  private static Set<String> loadCommonPasswords() {
    var stream = PasswordPolicy.class.getResourceAsStream("/common-passwords.txt");
    if (stream == null) {
      throw new IllegalStateException("common-passwords.txt não encontrado");
    }
    try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::trim)
          .filter(l -> !l.isEmpty() && !l.startsWith("#"))
          .map(l -> l.toLowerCase(Locale.ROOT))
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
