// Status efetivo de uma certificação, sempre calculado e nunca gravado (doc 1 §2.3.7).
package br.edu.ifba.agrofacil.certification;

public enum CertificationStatus {
  VALID,
  EXPIRING,
  EXPIRED,
  SUSPENDED,
  CANCELLED
}
