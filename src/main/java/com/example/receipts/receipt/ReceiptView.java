package com.example.receipts.receipt;

import java.time.Instant;

public record ReceiptView(long id, String originalFilename, String contentType, long sizeBytes, String sha256,
                          Instant uploadedAt, boolean processed, Instant processedAt, Long transactionId) {
    static ReceiptView of(Receipt r, Long transactionId) {
        return new ReceiptView(r.id(), r.originalFilename(), r.contentType(), r.sizeBytes(), r.sha256(),
                r.uploadedAt(), r.processed(), r.processedAt(), transactionId);
    }
}
