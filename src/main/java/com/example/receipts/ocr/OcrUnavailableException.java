package com.example.receipts.ocr;

public class OcrUnavailableException extends RuntimeException {
    public OcrUnavailableException(String message) {
        super(message);
    }
}
