-- MDAOPay Backend — V6 On-chain events index
--
-- Stores parsed contract events from polling. Used for transaction history,
-- proposal state, recovery tracking, and session key monitoring.
--
-- RETENTION: indefinite (immutable ledger). No PII stored.
-- Pruning: application-level if needed (unlikely for testnet).

CREATE TABLE onchain_events (
    id              BIGSERIAL PRIMARY KEY,
    chain_id        BIGINT NOT NULL,
    contract_address VARCHAR(42) NOT NULL,
    event_name      VARCHAR(100) NOT NULL,
    block_number    BIGINT NOT NULL,
    tx_hash         VARCHAR(66) NOT NULL,
    log_index       INT NOT NULL,
    wallet          VARCHAR(42),       -- denormalized indexed from-param for fast lookup
    params          JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(chain_id, tx_hash, log_index)
);

CREATE INDEX idx_events_wallet       ON onchain_events(wallet);
CREATE INDEX idx_events_contract     ON onchain_events(contract_address);
CREATE INDEX idx_events_name         ON onchain_events(event_name);
CREATE INDEX idx_events_block        ON onchain_events(block_number DESC);
CREATE INDEX idx_events_wallet_name  ON onchain_events(wallet, event_name);
CREATE INDEX idx_events_tx           ON onchain_events(tx_hash);
