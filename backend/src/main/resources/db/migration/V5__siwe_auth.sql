CREATE TABLE IF NOT EXISTS siwe_nonces (
    nonce VARCHAR(64) PRIMARY KEY,
    wallet_address VARCHAR(42) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
