// Cadastro do agricultor enviado pelo painel (doc 1 §3.7.4).
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.common.domain.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @param cpf com ou sem máscara; é guardado só com dígitos (RN02)
 * @param version obrigatória na edição
 */
public record FarmerWrite(
    @NotBlank @Size(max = 120) String name,
    @NotBlank String cpf,
    @Email @Size(max = 120) String email,
    @Pattern(regexp = "\\+\\d{10,15}") String phone,
    UUID groupId,
    @Min(1950) @Max(2100) Integer groupJoinedYear,
    @Size(max = 30) String cafNumber,
    @Valid Address address,
    @Size(max = 2000) String notes,
    Long version) {}
