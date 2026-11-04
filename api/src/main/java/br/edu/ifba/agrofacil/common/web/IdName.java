// Referência curta a outro recurso: { id, name }.
package br.edu.ifba.agrofacil.common.web;

import java.util.UUID;

public record IdName(UUID id, String name) {}
