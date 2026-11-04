// Dados de um comunicado enviados pelo painel (doc 1 §3.12).
package br.edu.ifba.agrofacil.announcement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @param groupId nulo = todos os agricultores; não muda depois de publicar
 * @param version obrigatório só na edição (PATCH)
 */
public record AnnouncementWrite(
    @NotNull AnnouncementType type,
    @NotBlank @Size(max = 80) String title,
    @NotBlank @Size(max = 2000) String body,
    UUID groupId,
    Long version) {}
