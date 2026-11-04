// Linha da lista de agricultores do painel, já com os indicadores (doc 1 §3.7.4).
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.common.web.IdName;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param cpfMasked ex.: {@code ***.456.789-**}
 * @param lastSyncAt último contato de qualquer aparelho
 * @param worstCertificationStatus pior status entre as propriedades; {@code NONE} se alguma não tem
 *     certificação; nulo se o agricultor não tem propriedade
 */
public record FarmerListItem(
    UUID id,
    String name,
    String cpfMasked,
    String phone,
    UserStatus status,
    IdName group,
    String municipalityName,
    long propertiesCount,
    Instant lastSyncAt,
    LocalDate lastRecordOn,
    long incompleteRecords,
    String worstCertificationStatus) {}
