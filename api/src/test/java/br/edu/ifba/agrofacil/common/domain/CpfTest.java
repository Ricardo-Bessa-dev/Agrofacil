// Testes da normalização, validação e máscara do CPF (RN02).
package br.edu.ifba.agrofacil.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CpfTest {

  @Test
  void validCpf_withOrWithoutMask_isAccepted_RN02() {
    assertThat(Cpf.isValid("529.982.247-25")).isTrue();
    assertThat(Cpf.isValid("52998224725")).isTrue();
  }

  @Test
  void wrongCheckDigits_repeatedDigitsAndWrongLength_areRejected_RN02() {
    assertThat(Cpf.isValid("529.982.247-24")).isFalse();
    assertThat(Cpf.isValid("111.111.111-11")).isFalse();
    assertThat(Cpf.isValid("1234567890")).isFalse();
    assertThat(Cpf.isValid(null)).isFalse();
  }

  @Test
  void normalize_keepsOnlyDigits() {
    assertThat(Cpf.normalize(" 529.982.247-25 ")).isEqualTo("52998224725");
  }

  @Test
  void mask_hidesFirstAndLastDigits() {
    assertThat(Cpf.mask("52998224725")).isEqualTo("***.982.247-**");
  }
}
