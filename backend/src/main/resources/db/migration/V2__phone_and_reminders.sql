-- Members' phone numbers (so librarians can call about overdue books),
-- and when a librarian last reminded someone about a loan.

ALTER TABLE users
    ADD COLUMN phone VARCHAR(20)
        CONSTRAINT users_phone_format CHECK (phone ~ '^\+?[0-9][0-9 ()-]{5,18}$');

ALTER TABLE loans
    ADD COLUMN last_reminded_at TIMESTAMPTZ;
