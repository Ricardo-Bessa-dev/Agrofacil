// Testes da regra de senha do coordenador (RN05).
package br.edu.ifba.agrofacil.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  @Test
  void shortOrCommonPasswords_areWeak_RN05() {
    assertThat(PasswordPolicy.isStrong("curta123")).isFalse();
    assertThat(PasswordPolicy.isStrong("1234567890")).isFalse();
    assertThat(PasswordPolicy.isStrong("Senha123456")).isFalse();
  }

  @Test
  void longUncommonPassword_isStrong_RN05() {
    assertThat(PasswordPolicy.isStrong("feijao-de-corda-na-roca")).isTrue();
  }
}
