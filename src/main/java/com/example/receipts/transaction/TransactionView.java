package com.example.receipts.transaction;

import com.example.receipts.domain.ItemizeStatus;
import com.example.receipts.domain.LineItem;
import com.example.receipts.domain.Money;
import com.example.receipts.domain.Reconciliation;
import com.example.receipts.domain.TaxLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TransactionView(long id, long receiptId, String merchant, LocalDate date, String currency,
                              BigDecimal grandTotal, ItemizeStatus itemizeStatus, List<TaxView> taxes,
                              List<ItemView> lineItems, ReconciliationView reconciliation,
                              Instant createdAt, Instant updatedAt) {
    public record TaxView(String name, BigDecimal rate, BigDecimal amount, boolean inclusive) {}
    public record ItemView(String description, BigDecimal amount, Integer quantity, BigDecimal taxAmount) {}

    static TransactionView of(TransactionHeader h, List<TaxLine> taxes, List<LineItem> items) {
        var rec = Reconciliation.of(h.header().grandTotalMinor(), taxes, items);
        return new TransactionView(h.id(), h.receiptId(), h.header().merchant(), h.header().date(),
                h.header().currency(), Money.fromMinor(h.header().grandTotalMinor()), h.status(),
                taxes.stream().map(t -> new TaxView(t.name(), t.rate(), Money.fromMinor(t.amountMinor()), t.inclusive())).toList(),
                items.stream().map(i -> new ItemView(i.description(), Money.fromMinor(i.amountMinor()), i.quantity(),
                        i.taxAmountMinor() == null ? null : Money.fromMinor(i.taxAmountMinor()))).toList(),
                ReconciliationView.of(rec), h.createdAt(), h.updatedAt());
    }
}
