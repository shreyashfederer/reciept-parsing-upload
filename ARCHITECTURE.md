# Architecture

## Data model
`receipts` (one row per distinct file, `UNIQUE(content_sha256)`) → `transactions` (`UNIQUE(receipt_id)`,
FK `ON DELETE RESTRICT`) → `taxes` and `line_items` (FK `ON DELETE CASCADE`, ordered by `position`).
Raw OCR text is stored on the receipt and is the only input to re-itemize. Money columns are `*_minor INTEGER`
(scale 2). Tax rates are stored as decimal strings (`0.19`). `itemize_status` has a `CHECK` constraint.

## Receipt → transaction identity
- **Receipt identity** is the SHA-256 of the uploaded bytes. `INSERT … ON CONFLICT(content_sha256) DO NOTHING`
  followed by a lookup returns the same `receipt_id` to every uploader of the same bytes, including concurrent ones.
- **Transaction identity** is `receipt_id`. The database enforces it with `transactions.receipt_id UNIQUE`.
  `process` writes the header with
  `INSERT … ON CONFLICT(receipt_id) DO UPDATE SET …` and then reads the id back by `receipt_id`. There is no
  code path that can insert a second row: a racing insert becomes an update of the existing row.
- A processed receipt cannot be deleted. The service returns 409, and the FK `ON DELETE RESTRICT` backs this up
  at the DB level, so money is never orphaned.

## Why concurrent `process` is safe
1. The unique constraint plus upsert makes "two transactions for one receipt" impossible at the storage layer.
2. Every DB transaction starts with `BEGIN IMMEDIATE` (`transaction_mode=IMMEDIATE`). The SQLite write lock is
   taken up front, so overlapping `process` calls serialize. Two deferred transactions that both read and then
   try to write would deadlock on lock upgrade (`SQLITE_BUSY`). `busy_timeout=10000` makes the second caller
   wait instead of failing. Transaction reads (`GET /transactions/…`) also run in a transaction, so header, taxes
   and items come from one consistent snapshot. As a result they also take the write lock and serialize with
   writers. That is acceptable at this scale. Single-statement reads (e.g. `GET /receipts/{id}`) run in autocommit
   and are not blocked, thanks to WAL.
3. Taxes and items are replaced (delete + insert) inside the same transaction as the header upsert. The later
   caller therefore sees and replaces the earlier caller's rows, and rows never accumulate.
4. No application locks, Redis, or lock service. With Postgres the same SQL shape works under row locks
   (`ON CONFLICT … DO UPDATE` locks the conflicting row).

Proven by `ConcurrencyTest`: 8 overlapping HTTP calls (repeated 3 times) → one transaction id, one tax row,
three item rows. The test also tries a direct duplicate `INSERT` and checks that the constraint rejects it.
Replacing the upsert with a plain `INSERT` makes this test fail with `UNIQUE constraint failed`.

## Why `process` is atomic
OCR and extraction run **before** the DB transaction, so slow work never holds the write lock. Then one
`TransactionTemplate` block runs these steps: re-check that the receipt exists, upsert the header, replace taxes,
replace items, and store the raw OCR plus `processed_at`. Any exception rolls back all of it. `ProcessAtomicityTest`
injects a failure while items are written and asserts there is no transaction, no tax rows, and that the receipt
is still unprocessed. An extraction failure (422) happens before the transaction opens, so nothing is written.

## Itemization rules
`Reconciliation`: `sum(items) + sum(exclusive taxes) == grand_total`, exact to the cent. Inclusive taxes
(`incl. VAT`) are already inside gross item amounts and are not added. Auto-itemize → `COMPLETE` only when the
result reconciles with at least one item, otherwise `NEEDS_REVIEW`. The printed total is never changed and no
balancing line is added. `PATCH /items` and `/complete` run the same check inside the same DB transaction as
their write, and return 409 with the reconciliation numbers when it fails. `/itemize` rewrites line items and
status only. Header and tax rows are never touched there.

## Trade-offs and next steps
- SQLite serializes all writers. That is fine here. Postgres would allow per-row concurrency with no code changes
  beyond the DataSource.
- Re-`process` overwrites user-edited items. A `source` column (AUTO/USER) or a version column for optimistic
  locking on PATCH would be the next addition.
- File delete happens after the DB commit. A crash between the two leaves an orphan file, never an orphan row.
  Every receipt row owns its own file (`<sha256>-<uuid>`). Deleting a receipt can therefore never remove the file
  of a newer receipt with the same bytes that was uploaded in between (`FileLifecycleTest`).
