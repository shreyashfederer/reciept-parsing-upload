package com.example.receipts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test
    void convertsToAndFromMinorUnits() {
        assertThat(Money.toMinor(new BigDecimal("17.85"))).isEqualTo(1785L);
        assertThat(Money.toMinor(new BigDecimal("24.0"))).isEqualTo(2400L);
        assertThat(Money.fromMinor(1785L)).isEqualTo(new BigDecimal("17.85"));
    }

    @Test
    void rejectsSubCentPrecisionInsteadOfRounding() {
        assertThatThrownBy(() -> Money.toMinor(new BigDecimal("3.505"))).isInstanceOf(ArithmeticException.class);
    }
}
