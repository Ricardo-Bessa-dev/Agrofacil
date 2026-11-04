// Código de acesso recém-gerado; é a única vez que ele aparece em texto (doc 1 §3.7.4).
package br.edu.ifba.agrofacil.user;

import java.time.Instant;

public record AccessCodeResponse(String code, Instant expiresAt) {}
