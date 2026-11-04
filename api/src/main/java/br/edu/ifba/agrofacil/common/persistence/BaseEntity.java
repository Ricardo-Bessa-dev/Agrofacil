// Entidade versionada e sincronizável: além de id, versão e datas, tem o sync_seq do pull.
package br.edu.ifba.agrofacil.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

/**
 * O {@code id} vem do cliente para o que o app cria offline; senão, o servidor gera (ver {@link
 * VersionedEntity}).
 */
@MappedSuperclass
@Audited
public abstract class BaseEntity extends VersionedEntity {

  /** Preenchido pelo trigger do banco a cada escrita (ADR-09); só leitura para o JPA. */
  @NotAudited
  @Column(insertable = false, updatable = false)
  private Long syncSeq;

  protected BaseEntity() {}

  protected BaseEntity(UUID id) {
    super(id);
  }

  public Long getSyncSeq() {
    return syncSeq;
  }
}
