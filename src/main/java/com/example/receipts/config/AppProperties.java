package com.example.receipts.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Path dataDir, int dbPoolSize) {
    public Path storageDir() {
        return dataDir.resolve("files");
    }
}
