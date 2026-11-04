// Produto coberto por uma certificação: uma cultura do catálogo ou um nome livre.
package br.edu.ifba.agrofacil.certification;

import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/** Linha de {@code certification_product}; pelo menos um dos dois campos vem preenchido. */
@Embeddable
public class CertificationProduct {

  private UUID cropId;
  private String productName;

  protected CertificationProduct() {}

  public CertificationProduct(UUID cropId, String productName) {
    this.cropId = cropId;
    this.productName = productName;
  }

  public UUID getCropId() {
    return cropId;
  }

  public String getProductName() {
    return productName;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof CertificationProduct other
        && Objects.equals(cropId, other.cropId)
        && Objects.equals(productName, other.productName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(cropId, productName);
  }
}
