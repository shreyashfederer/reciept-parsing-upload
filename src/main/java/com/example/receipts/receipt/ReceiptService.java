package com.example.receipts.receipt;

import com.example.receipts.transaction.TransactionRepository;
import com.example.receipts.web.BadRequestException;
import com.example.receipts.web.ConflictException;
import com.example.receipts.web.NotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReceiptService {
    public record UploadResult(long receiptId, boolean created) {}

    private final ReceiptRepository receipts;
    private final TransactionRepository transactions;
    private final FileStore files;
    private final TransactionTemplate tx;

    public ReceiptService(ReceiptRepository receipts, TransactionRepository transactions, FileStore files, TransactionTemplate tx) {
        this.receipts = receipts;
        this.transactions = transactions;
        this.files = files;
        this.tx = tx;
    }

    /** Dedupe by SHA-256 of the bytes; the UNIQUE(content_sha256) constraint settles races. */
    public UploadResult upload(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (bytes.length == 0) {
            throw new BadRequestException("Uploaded file is empty");
        }
        String sha = sha256Hex(bytes);
        Optional<Receipt> existing = receipts.findBySha256(sha);
        if (existing.isPresent()) {
            return new UploadResult(existing.get().id(), false);
        }
        Path path = files.save(sha, bytes);
        String filename = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        boolean created = receipts.insertIfAbsent(filename, file.getContentType(), sha, bytes.length, path.toString(), Instant.now());
        if (!created) {
            files.delete(path); // lost a concurrent-upload race; the winner's row points at its own file
        }
        long id = receipts.findBySha256(sha).orElseThrow().id();
        return new UploadResult(id, created);
    }

    public Receipt get(long id) {
        return receipts.findById(id).orElseThrow(() -> new NotFoundException("Receipt " + id + " not found"));
    }

    public Optional<Long> transactionIdFor(long receiptId) {
        return transactions.findIdByReceiptId(receiptId);
    }

    public String ocrText(long id) {
        Receipt r = get(id);
        if (r.ocrText() == null) {
            throw ConflictException.of("Receipt " + id + " has not been processed yet");
        }
        return r.ocrText();
    }

    public byte[] content(Receipt r) {
        return files.read(Path.of(r.storagePath()));
    }

    /** Row is removed in a DB transaction; the file is removed only after commit (a crash leaves an orphan file, never an orphan row). */
    public void delete(long id) {
        String storagePath = tx.execute(status -> {
            Receipt r = get(id);
            if (transactions.findIdByReceiptId(id).isPresent()) {
                throw ConflictException.of("Receipt " + id + " already has a transaction and cannot be deleted");
            }
            receipts.deleteById(id);
            return r.storagePath();
        });
        files.delete(Path.of(storagePath));
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
