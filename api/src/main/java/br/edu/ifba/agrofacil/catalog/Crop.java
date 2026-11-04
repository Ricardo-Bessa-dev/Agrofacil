// Cultura do catálogo (alface, feijão, umbu…); association_id nulo = global (ADR-07).
package br.edu.ifba.agrofacil.catalog;

import br.edu.ifba.agrofacil.common.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Não há exclusão: uma cultura sai de uso com {@code active = false}. */
@Entity
@Table(name = "crop")
public class Crop extends BaseEntity {

  private UUID associationId;
  private UUID categoryId;
  private String name;
  private String icon; // chave do ícone no app, ex.: lettuce
  private boolean active;

  protected Crop() {}

  /** Cultura criada pela associação (as globais vêm das migrações). */
  public Crop(UUID associationId) {
    super(UUID.randomUUID());
    this.associationId = associationId;
  }

  public void update(String name, String icon, UUID categoryId, boolean active) {
    this.name = name;
    this.icon = icon;
    this.categoryId = categoryId;
    this.active = active;
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public String getName() {
    return name;
  }

  public String getIcon() {
    return icon;
  }

  public boolean isActive() {
    return active;
  }
}
