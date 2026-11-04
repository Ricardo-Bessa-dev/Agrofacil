// Testes da comparação de versões do app (doc 2 §1.7.7).
package br.edu.ifba.agrofacil.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppVersionFilterTest {

  @Test
  void comparesNumericallyPartByPart() {
    assertThat(AppVersionFilter.compareVersions("1.4.0", "1.10.0")).isNegative();
    assertThat(AppVersionFilter.compareVersions("2.0", "1.9.9")).isPositive();
    assertThat(AppVersionFilter.compareVersions("1.0.0", "1.0")).isZero();
    assertThat(AppVersionFilter.compareVersions("1.2.3-beta", "1.2.3")).isZero();
  }
}
