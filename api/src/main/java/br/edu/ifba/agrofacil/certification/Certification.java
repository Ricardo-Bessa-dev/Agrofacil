// Certificação de uma propriedade; renovar cria uma nova linha com previous_id (RN18, RN19).
package br.edu.ifba.agrofacil.certification;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.persistence.SyncableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

/**
 * O status efetivo (VALID, EXPIRING…) nunca é gravado: vem do {@link
 * CertificationStatusCalculator}. Aqui fica só o status manual e as transições permitidas.
 */
@Entity
@Table(name = "certification")
@Audited
public class Certification extends SyncableEntity {

  /** Lote usado ao carregar produtos e trabalhadores de várias certificações de uma vez. */
  private static final int FETCH_BATCH = 50;

  private UUID propertyId;
  private String number;
  private LocalDate issuedOn;
  private LocalDate expiresOn;

  @Enumerated(EnumType.STRING)
  private ManualStatus manualStatus;

  private String statusReason;
  private String scope;
  private UUID previousId;
  private String notes;
  private UUID createdBy;

  @NotAudited
  @ElementCollection
  @BatchSize(size = FETCH_BATCH)
  @CollectionTable(
      name = "certification_product",
      joinColumns = @JoinColumn(name = "certification_id"))
  private List<CertificationProduct> products = new ArrayList<>();

  @NotAudited
  @ElementCollection
  @BatchSize(size = FETCH_BATCH)
  @CollectionTable(
      name = "certification_worker",
      joinColumns = @JoinColumn(name = "certification_id"))
  @Column(name = "worker_id")
  private Set<UUID> workerIds = new LinkedHashSet<>();

  protected Certification() {}

  public Certification(
      UUID propertyId, LocalDate issuedOn, LocalDate expiresOn, UUID previousId, UUID createdBy) {
    super(UUID.randomUUID());
    this.propertyId = propertyId;
    this.issuedOn = issuedOn;
    this.expiresOn = expiresOn;
    this.previousId = previousId;
    this.manualStatus = ManualStatus.ACTIVE;
    this.createdBy = createdBy;
  }

  /** Atualiza os dados editáveis; o status manual muda só pelas transições abaixo. */
  public void update(
      String number,
      LocalDate issuedOn,
      LocalDate expiresOn,
      String scope,
      String notes,
      List<CertificationProduct> products,
      Collection<UUID> workerIds) {
    this.number = number;
    this.issuedOn = issuedOn;
    this.expiresOn = expiresOn;
    this.scope = scope;
    this.notes = notes;
    this.products.clear();
    this.products.addAll(products);
    this.workerIds.clear();
    this.workerIds.addAll(workerIds);
  }

  /** RN19: suspender exige motivo e só vale para a certificação ativa. */
  public void suspend(String reason) {
    requireStatus(ManualStatus.ACTIVE);
    this.manualStatus = ManualStatus.SUSPENDED;
    this.statusReason = reason;
  }

  /** RN19: cancelar exige motivo; uma cancelada não volta. */
  public void cancel(String reason) {
    if (manualStatus == ManualStatus.CANCELLED) {
      throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
    }
    this.manualStatus = ManualStatus.CANCELLED;
    this.statusReason = reason;
  }

  /** Só a partir de {@code SUSPENDED} (doc 1 §3.10). */
  public void reactivate() {
    requireStatus(ManualStatus.SUSPENDED);
    this.manualStatus = ManualStatus.ACTIVE;
    this.statusReason = null;
  }

  private void requireStatus(ManualStatus expected) {
    if (manualStatus != expected) {
      throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
    }
  }

  public UUID getPropertyId() {
    return propertyId;
  }

  public String getNumber() {
    return number;
  }

  public LocalDate getIssuedOn() {
    return issuedOn;
  }

  public LocalDate getExpiresOn() {
    return expiresOn;
  }

  public ManualStatus getManualStatus() {
    return manualStatus;
  }

  public String getStatusReason() {
    return statusReason;
  }

  public String getScope() {
    return scope;
  }

  public UUID getPreviousId() {
    return previousId;
  }

  public String getNotes() {
    return notes;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public List<CertificationProduct> getProducts() {
    return List.copyOf(products);
  }

  public Set<UUID> getWorkerIds() {
    return Set.copyOf(workerIds);
  }
}
