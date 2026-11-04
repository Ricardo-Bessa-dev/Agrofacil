// Município pelo código IBGE (os da Bahia vêm da migração V4).
package br.edu.ifba.agrofacil.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "municipality")
public class Municipality {

  @Id private Integer id; // código IBGE (7 dígitos)
  private String name;

  @JdbcTypeCode(SqlTypes.CHAR) // coluna char(n)
  private String uf;

  protected Municipality() {}

  public Integer getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getUf() {
    return uf;
  }
}
