// Filtros da lista de agricultores (doc 1 §3.7.4).
package br.edu.ifba.agrofacil.user;

import java.util.UUID;

/**
 * @param search procura no nome (sem acento) e no CPF
 */
public record FarmerFilter(
    String search, UUID groupId, UserStatus status, Integer municipalityId) {}
