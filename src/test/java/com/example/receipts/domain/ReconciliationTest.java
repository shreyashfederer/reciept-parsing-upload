package com.example.receipts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconciliationTest {
    private static final TaxLine VAT_EXCL = new TaxLine("VAT", new BigDecimal("0.19"), 285, false);

    @Test
    void cleanReceiptReconcilesWithExclusiveTax() {
        var items = List.of(LineItem.of("Espresso", 350), LineItem.of("Sandwich", 890), LineItem.of("Mineral water", 260));
        var r = Reconciliation.of(1785, List.of(VAT_EXCL), items);
        assertThat(r.reconciles()).isTrue();
        assertThat(r.autoStatus(items.size())).isEqualTo(ItemizeStatus.COMPLETE);
    }

    @Test
    void mismatchReportsDifferenceAndNeedsReview() {
        var tax = new TaxLine("VAT", new BigDecimal("0.19"), 190, false);
        var items = List.of(LineItem.of("Water", 400), LineItem.of("Snacks", 600));
        var r = Reconciliation.of(1850, List.of(tax), items);
        assertThat(r.reconciles()).isFalse();
        assertThat(r.differenceMinor()).isEqualTo(660);
        assertThat(r.autoStatus(items.size())).isEqualTo(ItemizeStatus.NEEDS_REVIEW);
    }

    @Test
    void inclusiveTaxIsNotAddedOnTopOfGrossItems() {
        var tax = new TaxLine("VAT", new BigDecimal("0.19"), 383, true);
        var r = Reconciliation.of(2400, List.of(tax), List.of(LineItem.of("Trip fare", 2400)));
        assertThat(r.reconciles()).isTrue();
        assertThat(r.inclusiveTaxMinor()).isEqualTo(383);
    }

    @Test
    void noItemsAlwaysNeedsReview() {
        var r = Reconciliation.of(0, List.of(), List.of());
        assertThat(r.autoStatus(0)).isEqualTo(ItemizeStatus.NEEDS_REVIEW);
    }
}
