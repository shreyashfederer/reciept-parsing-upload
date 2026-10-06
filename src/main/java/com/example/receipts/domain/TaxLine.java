package com.example.receipts.domain;

import java.math.BigDecimal;

/** @param inclusive true when the tax is already contained in the item amounts ("incl. VAT"). */
public record TaxLine(String name, BigDecimal rate, long amountMinor, boolean inclusive) {}
