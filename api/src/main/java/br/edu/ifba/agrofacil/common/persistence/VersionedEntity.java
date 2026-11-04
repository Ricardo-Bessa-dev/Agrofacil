// Campos comuns das entidades editadas pelo painel: id, versão (edição simultânea) e datas.
package br.edu.ifba.agrofacil.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;

/**
 * Base das tabelas sem {@code sync_seq} (as do SPG que não vão para o app). As sincronizáveis
 * estendem {@link BaseEntity}. A versão é {@code Long} (e não {@code long}) para o Spring Data
 * saber que a entidade é nova quando {@code version == null}, mesmo com o id preenchido.
 */
@MappedSuperclass
@Audited
public abstract class VersionedEntity {

  @Id private UUID id;

  @Version private Long version;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected VersionedEntity() {}

  protected VersionedEntity(UUID id) {
    this.id = id;
  }

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  /**
   * Força uma nova versão mesmo sem mudança de coluna. Usado quando algo que pertence à entidade,
   * mas está em outra tabela, mudou.
   */
  public void touch() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public Long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
