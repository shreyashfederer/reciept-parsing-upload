package com.example.receipts.transaction;

import com.example.receipts.domain.LineItem;
import com.example.receipts.domain.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Full replacement list of line items; edit, merge, and split are all expressed as the new list. */
public record ItemsPatch(@NotNull List<@NotNull @Valid ItemInput> items) {

    public record ItemInput(
            @NotBlank @Size(max = 500) String description,
            @NotNull @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @Positive Integer quantity,
            @Digits(integer = 13, fraction = 2) BigDecimal taxAmount) {}

    public List<LineItem> toLineItems() {
        return items.stream().map(i -> new LineItem(i.description().strip(), Money.toMinor(i.amount()), i.quantity(),
                i.taxAmount() == null ? null : Money.toMinor(i.taxAmount()))).toList();
    }
}
