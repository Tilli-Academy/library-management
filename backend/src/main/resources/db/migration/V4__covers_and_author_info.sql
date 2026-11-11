-- Book covers (uploaded pictures) and an "about the author" text.

ALTER TABLE books
    ADD COLUMN about_author VARCHAR(2000),
    -- When the cover was last changed (NULL = no cover). Used in the picture URL so browsers fetch new ones.
    ADD COLUMN cover_updated_at TIMESTAMPTZ;

-- Pictures live in their own table, so loading book lists never loads image bytes.
CREATE TABLE book_covers (
    book_id      BIGINT      PRIMARY KEY REFERENCES books (id) ON DELETE CASCADE,
    content_type VARCHAR(30) NOT NULL CHECK (content_type IN ('image/jpeg')),
    data         BYTEA       NOT NULL CHECK (octet_length(data) BETWEEN 1 AND 3145728),
    updated_at   TIMESTAMPTZ NOT NULL
);
