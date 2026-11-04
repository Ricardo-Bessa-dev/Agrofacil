// Corpo do PUT /me/device, enviado pelo app ao abrir (doc 1 §3.4).
package br.edu.ifba.agrofacil.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param pushToken token do Expo; nulo se o agricultor negou as notificações
 */
public record MeDeviceUpdate(
    @Size(max = 200) String pushToken,
    @NotBlank @Size(max = 20) String appVersion,
    @Size(max = 80) String name) {}
