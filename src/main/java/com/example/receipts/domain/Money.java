package com.example.receipts.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Amounts are persisted as integer minor units with a fixed scale of 2 (cents). */
public final class Money {
    public static final int SCALE = 2;

    private Money() {}

    public static long toMinor(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
    }

    public static BigDecimal fromMinor(long minor) {
        return BigDecimal.valueOf(minor, SCALE);
    }
}
