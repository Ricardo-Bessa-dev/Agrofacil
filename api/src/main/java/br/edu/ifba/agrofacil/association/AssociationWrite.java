// Dados da associação que o coordenador edita no painel (doc 1 §3.7.1).
package br.edu.ifba.agrofacil.association;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Os limites seguem o contrato; {@code version} é obrigatório para detectar edição simultânea. */
public record AssociationWrite(
    @NotBlank @Size(max = 120) String name,
    @Pattern(regexp = "\\d{14}") String cnpj,
    @Email @Size(max = 120) String email,
    @Pattern(regexp = "\\+\\d{10,15}") String phone,
    @Pattern(regexp = "\\+\\d{10,15}") String whatsapp,
    @Size(max = 120) String opacName,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2)
        BigDecimal tarefaM2,
    @NotEmpty @Size(max = 10) List<@NotNull @Min(1) @Max(180) Integer> alertDays,
    @Min(1) @Max(168) int accessCodeTtlHours,
    @Min(3) int retentionYears,
    @Min(30) @Max(180) int visitWindowDays,
    @Min(0) @Max(12) int peerVisitsPerYear,
    @Min(1) @Max(5) int visitorsPerVisit,
    @Min(0) @Max(7) int visitReminderDays,
    boolean verificationSameGroupAllowed,
    @NotNull Long version) {}
