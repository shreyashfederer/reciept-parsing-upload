CREATE TABLE IF NOT EXISTS receipts (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  original_filename TEXT    NOT NULL,
  content_type      TEXT,
  content_sha256    TEXT    NOT NULL UNIQUE,
  size_bytes        INTEGER NOT NULL,
  storage_path      TEXT    NOT NULL,
  uploaded_at       TEXT    NOT NULL,
  ocr_text          TEXT,
  processed_at      TEXT
);

-- No AUTOINCREMENT: SQLite burns a sequence value on every upsert that resolves to an UPDATE, so each
-- re-process would leave an id gap. Transactions are never deleted, so rowid reuse cannot happen.
CREATE TABLE IF NOT EXISTS transactions (
  id                INTEGER PRIMARY KEY,
  receipt_id        INTEGER NOT NULL UNIQUE REFERENCES receipts(id) ON DELETE RESTRICT,
  merchant          TEXT    NOT NULL,
  txn_date          TEXT    NOT NULL,
  currency          TEXT    NOT NULL CHECK (length(currency) = 3),
  grand_total_minor INTEGER NOT NULL,
  itemize_status    TEXT    NOT NULL CHECK (itemize_status IN ('COMPLETE', 'NEEDS_REVIEW', 'FAILED')),
  created_at        TEXT    NOT NULL,
  updated_at        TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS taxes (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  transaction_id INTEGER NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
  position       INTEGER NOT NULL,
  name           TEXT    NOT NULL,
  rate           TEXT    NOT NULL,
  amount_minor   INTEGER NOT NULL,
  inclusive      INTEGER NOT NULL CHECK (inclusive IN (0, 1))
);

CREATE TABLE IF NOT EXISTS line_items (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  transaction_id   INTEGER NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
  position         INTEGER NOT NULL,
  description      TEXT    NOT NULL,
  amount_minor     INTEGER NOT NULL,
  quantity         INTEGER,
  tax_amount_minor INTEGER
);

CREATE INDEX IF NOT EXISTS idx_taxes_txn ON taxes(transaction_id);
CREATE INDEX IF NOT EXISTS idx_line_items_txn ON line_items(transaction_id);
