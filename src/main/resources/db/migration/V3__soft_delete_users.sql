ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;

DROP INDEX uq_users_username;
DROP INDEX uq_users_email;

CREATE UNIQUE INDEX uq_users_username ON users (lower(username)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_email    ON users (lower(email))    WHERE deleted_at IS NULL;
