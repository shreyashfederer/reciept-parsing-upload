package com.example.receipts.receipt;

import com.example.receipts.transaction.TransactionService;
import com.example.receipts.transaction.TransactionView;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/receipts")
public class ReceiptController {
    record UploadResponse(long receiptId, boolean duplicate) {}
    record OcrResponse(long receiptId, String ocrText) {}

    private final ReceiptService receipts;
    private final TransactionService transactions;

    public ReceiptController(ReceiptService receipts, TransactionService transactions) {
        this.receipts = receipts;
        this.transactions = transactions;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        ReceiptService.UploadResult result = receipts.upload(file);
        UploadResponse body = new UploadResponse(result.receiptId(), !result.created());
        return result.created()
                ? ResponseEntity.created(URI.create("/receipts/" + result.receiptId())).body(body)
                : ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ReceiptView get(@PathVariable long id) {
        Receipt r = receipts.get(id);
        return ReceiptView.of(r, receipts.transactionIdFor(id).orElse(null));
    }

    @GetMapping("/{id}/ocr")
    public OcrResponse ocr(@PathVariable long id) {
        return new OcrResponse(id, receipts.ocrText(id));
    }

    @PostMapping("/{id}/process")
    public TransactionView process(@PathVariable long id) {
        return transactions.process(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        receipts.delete(id);
        return ResponseEntity.noContent().build();
    }
}
