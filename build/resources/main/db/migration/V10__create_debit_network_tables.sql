-- Create debit_cards table
CREATE TABLE debit_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    card_number_masked VARCHAR(19) NOT NULL,
    card_holder_name VARCHAR(255) NOT NULL,
    expiry_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_ACTIVATION',
    daily_limit NUMERIC(19, 2) NOT NULL DEFAULT 2500.00,
    monthly_limit NUMERIC(19, 2) NOT NULL DEFAULT 25000.00,
    daily_used NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    monthly_used NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    daily_used_reset_date DATE,
    monthly_used_reset_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_debit_cards_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE INDEX idx_debit_cards_account_id ON debit_cards(account_id);
CREATE INDEX idx_debit_cards_customer_id ON debit_cards(customer_id);
CREATE INDEX idx_debit_cards_card_number ON debit_cards(card_number_masked);
CREATE INDEX idx_debit_cards_status ON debit_cards(status);

-- Create debit_transactions table
CREATE TABLE debit_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    debit_card_id UUID NOT NULL,
    account_id UUID NOT NULL,
    authorization_code VARCHAR(20) NOT NULL UNIQUE,
    network_reference_id VARCHAR(50),
    merchant_name VARCHAR(255) NOT NULL,
    merchant_category_code VARCHAR(10),
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    transaction_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'AUTHORIZED',
    decline_reason VARCHAR(50),
    authorized_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_debit_tx_card FOREIGN KEY (debit_card_id) REFERENCES debit_cards(id),
    CONSTRAINT fk_debit_tx_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE INDEX idx_debit_tx_card_id ON debit_transactions(debit_card_id);
CREATE INDEX idx_debit_tx_account_id ON debit_transactions(account_id);
CREATE INDEX idx_debit_tx_auth_code ON debit_transactions(authorization_code);
CREATE INDEX idx_debit_tx_status ON debit_transactions(status);
CREATE INDEX idx_debit_tx_authorized_at ON debit_transactions(authorized_at);

-- Create network_settlements table
CREATE TABLE network_settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_date DATE NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    transaction_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    batch_id VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_settlements_date ON network_settlements(settlement_date);
CREATE INDEX idx_settlements_status ON network_settlements(status);
CREATE INDEX idx_settlements_batch_id ON network_settlements(batch_id);

