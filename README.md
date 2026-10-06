# Receipt upload, taxes & auto-itemize

Spring Boot 3.5 / Java 21 service. It stores uploaded receipts and turns each one into **at most one**
transaction with separate tax rows and auto-itemized line items. Data lives in a file-backed SQLite DB
(`./data/receipts.db`). Uploaded files live under `./data/files/`.

**OCR is stubbed.** No OCR or LLM vendor is called, and no API key is needed. A text upload stands in for the
scanned image, and its contents are the OCR output (`StubTextOcrProvider`). An upload counts as text if its
content type is `text/*` or its filename ends in `.txt`. Extraction is a deterministic line parser
(`ReceiptExtractor`) that matches `fixtures/task-a/gold.json`. Images and PDFs can be uploaded and stored, but
`process` returns **422** for them.

## Prerequisites
JDK 21 on `PATH` or in `JAVA_HOME` (for example `brew install openjdk@21`). Maven is not needed because the
wrapper (`./mvnw`) is included.

## Run the API
```bash
./mvnw spring-boot:run          # http://localhost:8080
```
Optional: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.data-dir=/some/dir` relocates the DB and files.

## Run the tests
```bash
./mvnw test
```
Tests use a fresh temporary data directory and never touch `./data`.

## Rules and conventions
| Topic | Rule |
|---|---|
| Duplicate upload | Identical bytes (SHA-256) → **200** `{"receipt_id": <existing>, "duplicate": true}`. A new file → **201**. Enforced by `UNIQUE(content_sha256)`. |
| OCR before process | `GET /receipts/{id}/ocr` → **409** until `process` has run. |
| Process | Always **200** and returns the transaction. Re-processing updates the same row and **overwrites** items, including user edits. |
| Parse failure | Missing or invalid merchant/date/currency/total → **422**. Nothing is written. |
| Money | Stored as integer minor units, **scale 2**. JSON amounts are decimals (`17.85`). Tax `rate` is a fraction (`0.19`). |
| Net vs gross | `VAT 19%` printed after the subtotal → tax is *exclusive* and items are **net**. `incl. VAT 19%` → tax is *inclusive* and items are **gross**. |
| Reconciliation | `sum(items) + sum(exclusive taxes) == grand_total`, exact to the cent. Auto-itemize sets `COMPLETE` only if this holds and there is at least one item. Otherwise it sets `NEEDS_REVIEW`. No balancing line is ever added. |
| Tax-only receipt | No items are invented. The result is an empty item list plus `NEEDS_REVIEW`, and the user itemizes with `PATCH`. |
| PATCH items | The body is the full new item list (edit/merge/split). If it does not reconcile → **409** with a `reconciliation` payload, and nothing is persisted. A successful patch does not change the status. Call `/complete` to confirm. |
| `FAILED` status | Part of the model and DB check, but the deterministic stub never produces it. |
| Errors | RFC 7807 `application/problem+json`. |

## Example curls
```bash
# Health
curl -s localhost:8080/health

# Upload (201 new; 200 + "duplicate": true for identical bytes)
curl -s -F file=@fixtures/task-a/receipt-clean.txt    localhost:8080/receipts   # {"receipt_id":1,"duplicate":false}
curl -s -F file=@fixtures/task-a/receipt-mismatch.txt localhost:8080/receipts   # {"receipt_id":2,...}
curl -s -F file=@fixtures/task-a/receipt-tax-only.txt localhost:8080/receipts   # {"receipt_id":3,...}
curl -s -F file=@fixtures/task-a/receipt-clean.txt    localhost:8080/receipts   # {"receipt_id":1,"duplicate":true}

# Get receipt (processed=false, transaction_id=null before process)
curl -s localhost:8080/receipts/1

# OCR before process → 409
curl -s -i localhost:8080/receipts/1/ocr

# Process, then process again: same transaction id, no duplicate rows
curl -s -X POST localhost:8080/receipts/1/process
curl -s -X POST localhost:8080/receipts/1/process

# Stored OCR text
curl -s localhost:8080/receipts/1/ocr

# Receipt now shows transaction_id; look up the transaction both ways
curl -s localhost:8080/receipts/1
curl -s localhost:8080/transactions/1
curl -s "localhost:8080/transactions?receipt_id=1"

# Delete a processed receipt → 409 (the transaction is kept)
curl -s -i -X DELETE localhost:8080/receipts/1

# Delete an unprocessed receipt → 204
curl -s -F file=@README.md localhost:8080/receipts         # {"receipt_id":4,...}
curl -s -i -X DELETE localhost:8080/receipts/4

# Mismatch: NEEDS_REVIEW, grand_total stays 18.50
curl -s -X POST localhost:8080/receipts/2/process

# Complete on mismatch → 409, status unchanged
curl -s -i -X POST localhost:8080/transactions/2/complete

# PATCH that does not reconcile → 409 with reconciliation payload, nothing persisted
curl -s -i -X PATCH localhost:8080/transactions/2/items -H 'Content-Type: application/json' \
  -d '{"items":[{"description":"Water","amount":4.00},{"description":"Snacks","amount":6.00},{"description":"Minibar","amount":5.00}]}'

# PATCH that reconciles (10.00 + 6.60 + 1.90 VAT = 18.50) → 200, then complete → COMPLETE
curl -s -X PATCH localhost:8080/transactions/2/items -H 'Content-Type: application/json' \
  -d '{"items":[{"description":"Water","amount":4.00},{"description":"Snacks","amount":6.00},{"description":"Minibar","amount":6.60}]}'
curl -s -X POST localhost:8080/transactions/2/complete

# Re-run auto-itemize from stored OCR (replaces line items only)
curl -s -X POST localhost:8080/transactions/2/itemize

# Tax-only: NEEDS_REVIEW with no items
curl -s -X POST localhost:8080/receipts/3/process
```
Transaction ids are assigned in order of first processing. The ids above assume a fresh `./data` (`rm -rf data`)
and the order shown.

## Layout
`receipt/` upload, storage, receipt endpoints · `transaction/` process/itemize/patch/complete ·
`extraction/` text → header/taxes/items · `ocr/` stub provider · `domain/` money and reconciliation ·
`src/main/resources/schema.sql` constraints. See [ARCHITECTURE.md](ARCHITECTURE.md).
