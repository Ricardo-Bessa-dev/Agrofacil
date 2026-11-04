// Campos que o próprio usuário pode alterar em PATCH /me (doc 1 §3.4).
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.common.domain.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * O agricultor muda {@code phone}, {@code email} e {@code farmer.address}; o coordenador muda
 * {@code name} e {@code phone}. Os outros campos enviados são ignorados.
 */
public record MeWrite(
    @NotBlank @Size(max = 120) String name,
    @Email @Size(max = 120) String email,
    @Pattern(regexp = "\\+\\d{10,15}") String phone,
    @Valid FarmerPart farmer) {

  public record FarmerPart(@Valid Address address) {}
}
