-- Core schema: three tables only. Everything short-lived (OTP challenges,
-- failed-login counters, refresh tokens, rate-limit buckets) lives in Redis
-- under a TTL and is deliberately not modelled here.

CREATE TABLE users (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    fullname     VARCHAR(150) NOT NULL,
    username     VARCHAR(50)  NOT NULL,
    email        VARCHAR(255) NOT NULL,
    password     VARCHAR(100) NOT NULL,
    role         VARCHAR(20)  NOT NULL DEFAULT 'VIEWER',
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    mfa_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    locked_until TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role   CHECK (role   IN ('SUPER_ADMIN', 'EDITOR', 'CONTRIBUTOR', 'VIEWER')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

-- Case-insensitive uniqueness: "Farhan" and "farhan" are the same account.
CREATE UNIQUE INDEX uq_users_username ON users (lower(username));
CREATE UNIQUE INDEX uq_users_email    ON users (lower(email));

CREATE TABLE articles (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL,
    author_id  UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    status     VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_articles_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX idx_articles_author     ON articles (author_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_articles_status     ON articles (status)    WHERE deleted_at IS NULL;
CREATE INDEX idx_articles_created_at ON articles (created_at DESC);
CREATE INDEX idx_articles_title      ON articles (lower(title));

-- No foreign key on actor_id on purpose: an audit record must survive the
-- deletion of the user it describes, otherwise the trail can be erased.
CREATE TABLE audit_logs (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        UUID,
    actor_username  VARCHAR(100),
    action          VARCHAR(50) NOT NULL,
    resource_type   VARCHAR(50),
    resource_id     VARCHAR(100),
    http_method     VARCHAR(10),
    endpoint        VARCHAR(255),
    status          VARCHAR(10) NOT NULL,
    ip_address      VARCHAR(64),
    user_agent      TEXT,
    browser         VARCHAR(60),
    browser_version VARCHAR(40),
    os              VARCHAR(60),
    device_type     VARCHAR(20),
    detail          JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_status CHECK (status IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_audit_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_actor      ON audit_logs (actor_id, created_at DESC);
CREATE INDEX idx_audit_action     ON audit_logs (action, created_at DESC);
CREATE INDEX idx_audit_resource   ON audit_logs (resource_type, resource_id);
