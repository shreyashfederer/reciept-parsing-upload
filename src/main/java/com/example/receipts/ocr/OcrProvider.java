package com.example.receipts.ocr;

public interface OcrProvider {
    String extractText(String filename, String contentType, byte[] content);
}
