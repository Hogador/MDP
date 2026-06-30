CREATE TABLE onramp_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    provider_id VARCHAR(50) NOT NULL,
    quote_id VARCHAR(100),
    fiat_currency VARCHAR(3) NOT NULL,
    crypto_currency VARCHAR(10) NOT NULL,
    chain_id INT,
    fiat_amount NUMERIC(18,2),
    crypto_amount NUMERIC(78,0),
    fee NUMERIC(18,2),
    rate NUMERIC(36,18),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    provider_ref VARCHAR(100),
    destination_address VARCHAR(42),
    redirect_url TEXT,
    widget_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_onramp_orders_user ON onramp_orders(user_id);
CREATE INDEX idx_onramp_orders_status ON onramp_orders(status);
CREATE INDEX idx_onramp_orders_provider_ref ON onramp_orders(provider_ref);
