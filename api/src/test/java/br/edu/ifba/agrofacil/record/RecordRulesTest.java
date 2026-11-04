// Testes das regras puras dos registros: datas (RN10), obrigatórios (RN09) e completude (doc 1
// §2.5).
package br.edu.ifba.agrofacil.record;

import static org.assertj.core.api.Assertions.assertThat;

import br.edu.ifba.agrofacil.common.domain.QuantityValue;
import br.edu.ifba.agrofacil.common.error.FieldErrorItem;
import br.edu.ifba.agrofacil.record.RecordEnums.Completeness;
import br.edu.ifba.agrofacil.record.RecordEnums.RecordType;
import br.edu.ifba.agrofacil.record.RecordEnums.SeedOrigin;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordRulesTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

  @Test
  void tomorrow_isTolerated_butTwoDaysAhead_isInTheFuture_RN10() {
    assertThat(RecordRules.checkDate("performedOn", TODAY.plusDays(1), TODAY)).isEmpty();
    assertThat(RecordRules.checkDate("performedOn", TODAY.plusDays(2), TODAY))
        .extracting(FieldErrorItem::code)
        .containsExactly("DATE_IN_FUTURE");
  }

  @Test
  void before2000_isTooOld_RN10() {
    assertThat(RecordRules.checkDate("performedOn", LocalDate.of(1999, 12, 31), TODAY))
        .extracting(FieldErrorItem::code)
        .containsExactly("DATE_TOO_OLD");
  }

  @Test
  void planting_withoutPlotOrCrop_isMissingRequiredFields_RN09() {
    var write =
        new RecordWrite.PlantingWrite(
            null,
            RecordType.PLANTING,
            UUID.randomUUID(),
            null,
            TODAY,
            null,
            null,
            null,
            null,
            null,
            " ",
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(RecordRules.checkRequiredParts(write))
        .extracting(FieldErrorItem::field)
        .containsExactlyInAnyOrder("plotId", "cropId");
  }

  @Test
  void application_acceptsPlotInsteadOfPlanting_RN09() {
    var write =
        new RecordWrite.InputApplicationWrite(
            null,
            RecordType.INPUT_APPLICATION,
            UUID.randomUUID(),
            UUID.randomUUID(),
            TODAY,
            null,
            null,
            null,
            null,
            null,
            null,
            "Calda caseira",
            null,
            null,
            null);
    assertThat(RecordRules.checkRequiredParts(write)).isEmpty();
  }

  @Test
  void planting_listsRecommendedFieldsThatAreMissing_RN09() {
    var planting = new Planting(UUID.randomUUID());
    planting.setFields(UUID.randomUUID(), null, null, null, null, null, SeedOrigin.OWN, null, null);
    planting.refreshCompleteness(false);
    assertThat(planting.getCompleteness()).isEqualTo(Completeness.INCOMPLETE);
    assertThat(planting.getMissingFields()).containsExactly("plantedArea", "expectedHarvestOn");
  }

  @Test
  void purchase_withReceiptPhoto_doesNotNeedSupplier_RN09() {
    var purchase = new InputPurchase(UUID.randomUUID());
    purchase.setFields(UUID.randomUUID(), null, new QuantityValue(BigDecimal.TEN, "BAG"), null);
    purchase.refreshCompleteness(false);
    assertThat(purchase.getMissingFields()).containsExactly(InputPurchase.SUPPLIER_OR_RECEIPT);
    purchase.refreshCompleteness(true);
    assertThat(purchase.getCompleteness()).isEqualTo(Completeness.COMPLETE);
  }
}
