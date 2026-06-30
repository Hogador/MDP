-- MDAOPay Backend — V1 Initial Schema
--
-- RETENTION POLICY (F-044 / GDPR Art. 17):
--   users.email / google_sub / apple_sub — PII.
--   DELETE FROM users WHERE id = ?   — right to erasure
--   DELETE FROM audit_log WHERE created_at < NOW() - INTERVAL '90 days'
--   Data anonymization upon user deletion request:
--     UPDATE users SET email=NULL, google_sub=NULL, apple_sub=NULL, updated_at=NOW()
--     WHERE id = ?
--   Retention: 90 days for audit_log, indefinite for transactions (immutable ledger).
--   Schema-level auto-purging is handled by application layer.
--   All PII columns MUST be reviewed when adding new columns to users table.
--

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_address VARCHAR(42) UNIQUE NOT NULL,
    email VARCHAR(255),
    google_sub VARCHAR(255) UNIQUE,
    apple_sub VARCHAR(255) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE nicknames (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nickname VARCHAR(20) UNIQUE NOT NULL,
    address VARCHAR(42) NOT NULL,
    resolved BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_nicknames_address ON nicknames(address);
CREATE INDEX idx_nicknames_nickname_lower ON nicknames(LOWER(nickname));

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tx_hash VARCHAR(66) UNIQUE,
    chain_id INT NOT NULL,
    from_address VARCHAR(42) NOT NULL,
    to_address VARCHAR(42),
    value NUMERIC(78,0),
    token_address VARCHAR(42),
    token_symbol VARCHAR(10),
    token_decimals INT,
    status VARCHAR(20) NOT NULL,
    block_number BIGINT,
    timestamp TIMESTAMPTZ,
    gas_used BIGINT,
    gas_price NUMERIC(78,0),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_transactions_from ON transactions(from_address);
CREATE INDEX idx_transactions_hash ON transactions(tx_hash);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_from_time ON transactions(from_address, created_at DESC);

CREATE TABLE guardians (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID REFERENCES users(id),
    guardian_wallet_id UUID REFERENCES users(id),
    share_index SMALLINT CHECK (share_index BETWEEN 1 AND 4),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    invite_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ
);

CREATE TABLE recovery_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID REFERENCES users(id),
    status VARCHAR(20) NOT NULL,
    threshold SMALLINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE recovery_shares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID REFERENCES users(id),
    share_index SMALLINT CHECK (share_index BETWEEN 1 AND 4),
    share_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE audit_log (
    id BIGSERIAL,
    event_type VARCHAR(50) NOT NULL,
    actor_address VARCHAR(42),
    payload JSONB,
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

CREATE INDEX idx_audit_log_type_created ON audit_log(event_type, created_at DESC);
