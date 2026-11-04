// Grupo do SPG no formato do contrato (doc 1 §3.7.3).
package br.edu.ifba.agrofacil.group;

import java.util.UUID;

/**
 * @param farmersCount agricultores ativos no grupo
 * @param belowMinimum menos de 3 agricultores (aviso da RN01)
 */
public record GroupView(
    UUID id,
    String name,
    Integer municipalityId,
    String municipalityName,
    String notes,
    long farmersCount,
    boolean belowMinimum,
    Long version) {

  public GroupWrite toWrite() {
    return new GroupWrite(name, municipalityId, notes, version);
  }
}
