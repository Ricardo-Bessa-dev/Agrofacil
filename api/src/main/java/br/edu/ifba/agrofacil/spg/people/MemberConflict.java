// Impedimento entre dois agricultores (parente, mesma casa, sócio): um não visita o outro (RN32).
package br.edu.ifba.agrofacil.spg.people;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * O par é guardado em ordem ({@code farmer_a_id < farmer_b_id}) para o banco impedir duplicatas. O
 * motivo só aparece no painel; a sugestão de visitadores mostra um texto genérico (RN40).
 */
@Entity
@Table(name = "member_conflict")
public class MemberConflict {

  public enum Reason {
    FAMILY,
    SAME_HOUSEHOLD,
    BUSINESS,
    OTHER
  }

  @Id private UUID id;
  private UUID associationId;

  // Nomes explícitos: a estratégia padrão geraria "farmeraid" (letra solta antes de "Id").
  @Column(name = "farmer_a_id")
  private UUID farmerAId;

  @Column(name = "farmer_b_id")
  private UUID farmerBId;

  @Enumerated(EnumType.STRING)
  private Reason reason;

  private String notes;
  private Instant createdAt;

  protected MemberConflict() {}

  /** Ordena o par, como o banco exige. */
  public MemberConflict(UUID associationId, UUID one, UUID other, Reason reason, String notes) {
    this.id = UUID.randomUUID();
    this.associationId = associationId;
    // Atenção: UUID.compareTo do Java compara com sinal e diverge do Postgres, que compara os
    // bytes sem sinal. O texto canônico (hex minúsculo) segue a mesma ordem do banco.
    boolean ordered = one.toString().compareTo(other.toString()) < 0;
    this.farmerAId = ordered ? one : other;
    this.farmerBId = ordered ? other : one;
    this.reason = reason;
    this.notes = notes;
  }

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }

  /** O outro lado do impedimento, ou nulo se a pessoa não faz parte dele. */
  public UUID otherThan(UUID farmerId) {
    if (farmerAId.equals(farmerId)) {
      return farmerBId;
    }
    return farmerBId.equals(farmerId) ? farmerAId : null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAssociationId() {
    return associationId;
  }

  public UUID getFarmerAId() {
    return farmerAId;
  }

  public UUID getFarmerBId() {
    return farmerBId;
  }

  public Reason getReason() {
    return reason;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
