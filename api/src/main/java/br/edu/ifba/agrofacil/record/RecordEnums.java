// Enumerações dos registros de manejo (doc 1 §2.3.8 e §3.9.1).
package br.edu.ifba.agrofacil.record;

/** Agrupadas num arquivo só porque são curtas e só fazem sentido juntas. */
public final class RecordEnums {

  private RecordEnums() {}

  public enum RecordType {
    PLANTING,
    INPUT_APPLICATION,
    HARVEST,
    INPUT_PURCHASE,
    OUTFLOW
  }

  /** De onde veio a escrita: app (push) ou painel. */
  public enum RecordSource {
    MOBILE,
    WEB
  }

  /** Selo "Completo" (ADR-06). */
  public enum Completeness {
    COMPLETE,
    INCOMPLETE
  }

  public enum PropagationType {
    SEED,
    SEEDLING,
    CUTTING,
    OTHER
  }

  public enum SeedOrigin {
    OWN,
    ORGANIC_PURCHASED,
    CONVENTIONAL_PURCHASED,
    SEED_BANK
  }

  /** Plantio ativo ou encerrado pela colheita final (RN12). */
  public enum CycleStatus {
    ACTIVE,
    FINISHED
  }

  public enum ApplicationPurpose {
    FERTILIZATION,
    PEST_DISEASE_CONTROL,
    SOIL_CORRECTION,
    OTHER
  }

  public enum OutflowDestination {
    FAIR,
    COOPERATIVE,
    PNAE,
    PAA,
    MIDDLEMAN,
    DIRECT_SALE,
    OWN_CONSUMPTION,
    DONATION,
    OTHER
  }
}
