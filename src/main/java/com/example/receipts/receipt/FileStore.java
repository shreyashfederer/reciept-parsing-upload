package com.example.receipts.receipt;

import com.example.receipts.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Local file storage, written atomically. Every save gets its own file, so deleting one receipt's file can never
 * remove the file of a later receipt with the same bytes.
 */
@Component
public class FileStore {
    private final Path root;

    public FileStore(AppProperties props) throws IOException {
        this.root = props.storageDir();
        Files.createDirectories(root);
    }

    public Path save(String sha256, byte[] bytes) {
        Path target = root.resolve(sha256 + "-" + UUID.randomUUID());
        try {
            Path tmp = Files.createTempFile(root, sha256, ".part");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
