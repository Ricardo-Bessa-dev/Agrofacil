// Categoria de cultura: hortaliça, fruta, grão…
package br.edu.ifba.agrofacil.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "crop_category")
public class CropCategory {

  @Id private UUID id;
  private String name;
  private String icon;

  protected CropCategory() {}

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getIcon() {
    return icon;
  }
}
