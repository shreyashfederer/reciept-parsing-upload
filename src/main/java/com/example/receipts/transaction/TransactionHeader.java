package com.example.receipts.transaction;

import com.example.receipts.domain.ItemizeStatus;
import com.example.receipts.domain.ReceiptHeader;
import java.time.Instant;

public record TransactionHeader(long id, long receiptId, ReceiptHeader header, ItemizeStatus status,
                                Instant createdAt, Instant updatedAt) {}
