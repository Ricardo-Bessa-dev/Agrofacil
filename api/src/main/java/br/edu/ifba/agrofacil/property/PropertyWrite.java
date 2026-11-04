// Dados de uma propriedade enviados pelo painel ou pelo push do app (doc 1 §3.8).
package br.edu.ifba.agrofacil.property;

import br.edu.ifba.agrofacil.common.domain.Address;
import br.edu.ifba.agrofacil.common.domain.Area;
import br.edu.ifba.agrofacil.common.domain.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @param id gerado no app (push); no painel é ignorado na criação
 * @param farmerId dono; no push do agricultor é ignorado (vale sempre o próprio)
 * @param version obrigatória na edição pelo painel
 */
public record PropertyWrite(
    UUID id,
    UUID farmerId,
    @NotBlank @Size(max = 80) String name,
    @Valid Area totalArea,
    @Size(max = 200) String productionDescription,
    @Valid GeoPoint location,
    @Valid Address address,
    @Size(max = 1000) String accessDirections,
    Long version) {}
