package com.example.receipts.receipt;

import java.time.Instant;

public record Receipt(long id, String originalFilename, String contentType, String sha256, long sizeBytes,
                      String storagePath, Instant uploadedAt, String ocrText, Instant processedAt) {
    public boolean processed() {
        return processedAt != null;
    }
}
