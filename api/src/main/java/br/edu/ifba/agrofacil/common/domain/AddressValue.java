// Endereço gravado no banco, com os mesmos nomes de coluna no agricultor e na propriedade.
package br.edu.ifba.agrofacil.common.domain;

import jakarta.persistence.Embeddable;
import java.util.function.IntFunction;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
public class AddressValue {

  @JdbcTypeCode(SqlTypes.CHAR) // coluna char(n)
  private String postalCode;

  @JdbcTypeCode(SqlTypes.CHAR) // coluna char(n)
  private String uf;

  private Integer municipalityId;
  private String locality;
  private String street;
  private String number;
  private String complement;

  protected AddressValue() {}

  public AddressValue(
      String postalCode,
      String uf,
      Integer municipalityId,
      String locality,
      String street,
      String number,
      String complement) {
    this.postalCode = postalCode;
    this.uf = uf;
    this.municipalityId = municipalityId;
    this.locality = locality;
    this.street = street;
    this.number = number;
    this.complement = complement;
  }

  public static AddressValue from(Address a) {
    if (a == null) {
      return null;
    }
    return new AddressValue(
        a.postalCode(),
        a.uf(),
        a.municipalityId(),
        a.locality(),
        a.street(),
        a.number(),
        a.complement());
  }

  /**
   * @param municipalityName resolve o nome do município pelo código IBGE
   */
  public static Address toDto(AddressValue v, IntFunction<String> municipalityName) {
    if (v == null) {
      return Address.empty();
    }
    return new Address(
        v.postalCode,
        v.uf,
        v.municipalityId,
        v.municipalityId == null ? null : municipalityName.apply(v.municipalityId),
        v.locality,
        v.street,
        v.number,
        v.complement);
  }

  public Integer getMunicipalityId() {
    return municipalityId;
  }

  public String getLocality() {
    return locality;
  }
}
