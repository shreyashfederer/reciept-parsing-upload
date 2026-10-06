package com.example.receipts.web;

import java.util.Map;

public class ConflictException extends RuntimeException {
    private final Map<String, Object> properties;

    public ConflictException(String detail, Map<String, Object> properties) {
        super(detail);
        this.properties = Map.copyOf(properties);
    }

    public static ConflictException of(String detail) {
        return new ConflictException(detail, Map.of());
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
