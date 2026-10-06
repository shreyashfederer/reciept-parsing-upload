package com.example.receipts.domain;

import java.util.List;

/** Items reconcile when sum(items) + sum(exclusive taxes) == grand total, exactly, in minor units. */
public record Reconciliation(long grandTotalMinor, long itemsTotalMinor, long exclusiveTaxMinor, long inclusiveTaxMinor) {

    public static Reconciliation of(long grandTotalMinor, List<TaxLine> taxes, List<LineItem> items) {
        long itemsTotal = items.stream().mapToLong(LineItem::amountMinor).sum();
        long exclusive = taxes.stream().filter(t -> !t.inclusive()).mapToLong(TaxLine::amountMinor).sum();
        long inclusive = taxes.stream().filter(TaxLine::inclusive).mapToLong(TaxLine::amountMinor).sum();
        return new Reconciliation(grandTotalMinor, itemsTotal, exclusive, inclusive);
    }

    public long differenceMinor() {
        return grandTotalMinor - itemsTotalMinor - exclusiveTaxMinor;
    }

    public boolean reconciles() {
        return differenceMinor() == 0;
    }

    /** Status assigned by auto-itemize: never COMPLETE without at least one item. */
    public ItemizeStatus autoStatus(int itemCount) {
        return itemCount > 0 && reconciles() ? ItemizeStatus.COMPLETE : ItemizeStatus.NEEDS_REVIEW;
    }
}
