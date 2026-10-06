package com.example.receipts.domain;

public record LineItem(String description, long amountMinor, Integer quantity, Long taxAmountMinor) {
    public static LineItem of(String description, long amountMinor) {
        return new LineItem(description, amountMinor, null, null);
    }
}
