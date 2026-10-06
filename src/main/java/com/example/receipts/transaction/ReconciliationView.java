package com.example.receipts.transaction;

import com.example.receipts.domain.Money;
import com.example.receipts.domain.Reconciliation;
import java.math.BigDecimal;

public record ReconciliationView(BigDecimal grandTotal, BigDecimal itemsTotal, BigDecimal exclusiveTaxTotal,
                                 BigDecimal inclusiveTaxTotal, BigDecimal difference, boolean reconciles) {
    public static ReconciliationView of(Reconciliation r) {
        return new ReconciliationView(Money.fromMinor(r.grandTotalMinor()), Money.fromMinor(r.itemsTotalMinor()),
                Money.fromMinor(r.exclusiveTaxMinor()), Money.fromMinor(r.inclusiveTaxMinor()),
                Money.fromMinor(r.differenceMinor()), r.reconciles());
    }
}
