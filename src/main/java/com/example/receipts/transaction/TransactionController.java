package com.example.receipts.transaction;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final TransactionService transactions;

    public TransactionController(TransactionService transactions) {
        this.transactions = transactions;
    }

    @GetMapping("/{id}")
    public TransactionView get(@PathVariable long id) {
        return transactions.get(id);
    }

    @GetMapping
    public TransactionView byReceipt(@RequestParam("receipt_id") long receiptId) {
        return transactions.getByReceiptId(receiptId);
    }

    @PostMapping("/{id}/itemize")
    public TransactionView itemize(@PathVariable long id) {
        return transactions.reitemize(id);
    }

    @PatchMapping("/{id}/items")
    public TransactionView patchItems(@PathVariable long id, @Valid @RequestBody ItemsPatch body) {
        return transactions.replaceItems(id, body.toLineItems());
    }

    @PostMapping("/{id}/complete")
    public TransactionView complete(@PathVariable long id) {
        return transactions.complete(id);
    }
}
