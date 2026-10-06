package com.example.receipts.domain;

import java.time.LocalDate;

public record ReceiptHeader(String merchant, LocalDate date, String currency, long grandTotalMinor) {}
