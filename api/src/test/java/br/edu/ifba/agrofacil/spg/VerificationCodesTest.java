// Testes do código de verificação dos documentos: alfabeto sem letras ambíguas e formato.
package br.edu.ifba.agrofacil.spg;

import static org.assertj.core.api.Assertions.assertThat;

import br.edu.ifba.agrofacil.document.VerificationCodes;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class VerificationCodesTest {

  @RepeatedTest(20)
  void code_hasEightCharsWithoutAmbiguousLetters() {
    assertThat(VerificationCodes.next()).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}");
  }

  @Test
  void display_andNormalize_roundTrip() {
    assertThat(VerificationCodes.display("K7QM4TZP")).isEqualTo("K7QM-4TZP");
    assertThat(VerificationCodes.normalize(" k7qm-4tzp ")).isEqualTo("K7QM4TZP");
  }
}
