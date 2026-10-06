package com.example.receipts.transaction;

import com.example.receipts.domain.ItemizeStatus;
import com.example.receipts.domain.LineItem;
import com.example.receipts.domain.Reconciliation;
import com.example.receipts.extraction.Extraction;
import com.example.receipts.extraction.ReceiptExtractor;
import com.example.receipts.ocr.OcrProvider;
import com.example.receipts.receipt.Receipt;
import com.example.receipts.receipt.ReceiptRepository;
import com.example.receipts.receipt.ReceiptService;
import com.example.receipts.web.ConflictException;
import com.example.receipts.web.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransactionService {
    private final ReceiptService receiptService;
    private final ReceiptRepository receipts;
    private final TransactionRepository transactions;
    private final OcrProvider ocr;
    private final ReceiptExtractor extractor;
    private final TransactionTemplate tx;

    public TransactionService(ReceiptService receiptService, ReceiptRepository receipts, TransactionRepository transactions,
                              OcrProvider ocr, ReceiptExtractor extractor, TransactionTemplate tx) {
        this.receiptService = receiptService;
        this.receipts = receipts;
        this.transactions = transactions;
        this.ocr = ocr;
        this.extractor = extractor;
        this.tx = tx;
    }

    /**
     * OCR and extraction run outside the DB transaction (slow, no locks held). The structured result
     * (header, taxes, items, raw OCR) is then written in one commit. The UNIQUE(receipt_id) upsert keeps
     * concurrent calls to a single row.
     */
    public TransactionView process(long receiptId) {
        Receipt receipt = receiptService.get(receiptId);
        String text = ocr.extractText(receipt.originalFilename(), receipt.contentType(), receiptService.content(receipt));
        Extraction ex = extractor.extract(text);
        ItemizeStatus status = Reconciliation.of(ex.header().grandTotalMinor(), ex.taxes(), ex.items())
                .autoStatus(ex.items().size());

        return tx.execute(s -> {
            if (receipts.findById(receiptId).isEmpty()) {
                throw new NotFoundException("Receipt " + receiptId + " not found");
            }
            Instant now = Instant.now();
            long id = transactions.upsertHeader(receiptId, ex.header(), status, now);
            transactions.replaceTaxes(id, ex.taxes());
            transactions.replaceItems(id, ex.items());
            receipts.markProcessed(receiptId, text, now);
            return view(require(id));
        });
    }

    public TransactionView get(long id) {
        return tx.execute(s -> view(require(id)));
    }

    public TransactionView getByReceiptId(long receiptId) {
        return tx.execute(s -> {
            long id = transactions.findIdByReceiptId(receiptId)
                    .orElseThrow(() -> new NotFoundException("No transaction for receipt " + receiptId));
            return view(require(id));
        });
    }

    /** Re-runs auto-itemize from the stored OCR text. Replaces line items only; header and taxes are untouched. */
    public TransactionView reitemize(long id) {
        return tx.execute(s -> {
            TransactionHeader h = require(id);
            String ocrText = receipts.findById(h.receiptId()).map(Receipt::ocrText)
                    .orElseThrow(() -> new IllegalStateException("Transaction " + id + " has no stored OCR"));
            List<LineItem> items = extractor.extractItems(ocrText);
            Reconciliation rec = Reconciliation.of(h.header().grandTotalMinor(), transactions.findTaxes(id), items);
            transactions.replaceItems(id, items);
            transactions.updateStatus(id, rec.autoStatus(items.size()), Instant.now());
            return view(require(id));
        });
    }

    /** User override. Rejected with 409 (and nothing written) unless items + stored taxes reconcile with grand_total. */
    public TransactionView replaceItems(long id, List<LineItem> items) {
        return tx.execute(s -> {
            TransactionHeader h = require(id);
            Reconciliation rec = Reconciliation.of(h.header().grandTotalMinor(), transactions.findTaxes(id), items);
            if (!rec.reconciles()) {
                throw mismatch("Line items plus stored taxes do not reconcile with grand_total; patch not applied", rec);
            }
            transactions.replaceItems(id, items);
            transactions.touch(id, Instant.now());
            return view(require(id));
        });
    }

    /** Sets COMPLETE only if the persisted items + stored taxes reconcile; otherwise 409 and status unchanged. */
    public TransactionView complete(long id) {
        return tx.execute(s -> {
            TransactionHeader h = require(id);
            List<LineItem> items = transactions.findItems(id);
            Reconciliation rec = Reconciliation.of(h.header().grandTotalMinor(), transactions.findTaxes(id), items);
            if (items.isEmpty() || !rec.reconciles()) {
                throw mismatch("Itemization does not reconcile with grand_total; status unchanged", rec);
            }
            transactions.updateStatus(id, ItemizeStatus.COMPLETE, Instant.now());
            return view(require(id));
        });
    }

    private static ConflictException mismatch(String detail, Reconciliation rec) {
        return new ConflictException(detail, Map.of("reconciliation", ReconciliationView.of(rec)));
    }

    private TransactionHeader require(long id) {
        return transactions.findById(id).orElseThrow(() -> new NotFoundException("Transaction " + id + " not found"));
    }

    private TransactionView view(TransactionHeader h) {
        return TransactionView.of(h, transactions.findTaxes(h.id()), transactions.findItems(h.id()));
    }
}
