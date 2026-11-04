// Perfil do usuário logado (doc 1 §3.4); também vai no pull do agricultor.
package br.edu.ifba.agrofacil.user;

import br.edu.ifba.agrofacil.common.domain.Address;
import br.edu.ifba.agrofacil.common.security.Role;
import br.edu.ifba.agrofacil.common.web.IdName;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param currentTermsVersion se for diferente de {@code termsAcceptedVersion}, o app mostra o termo
 *     de novo
 */
public record Me(
    UUID id,
    Role role,
    String name,
    String cpf,
    String email,
    String phone,
    MeAssociation association,
    MeFarmer farmer,
    String termsAcceptedVersion,
    String currentTermsVersion) {

  /**
   * @param whatsapp E.164; usado no link "Fale com a associação"
   */
  public record MeAssociation(UUID id, String name, String whatsapp, BigDecimal tarefaM2) {}

  public record MeFarmer(IdName group, String cafNumber, Address address) {}
}
