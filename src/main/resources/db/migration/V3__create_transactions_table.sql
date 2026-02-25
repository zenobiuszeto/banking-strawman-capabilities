-- Create transactions table for banking platform
-- Stores complete transaction history and audit trail

CREATE TABLE transactions (
    id UUID NOT NULL PRIMARY KEY,
    account_id UUID NOT NULL,
    related_account_id UUID,
    reference_number VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    running_balance NUMERIC(19, 2) NOT NULL,
    description VARCHAR(500),
    merchant_name VARCHAR(255),
    merchant_category VARCHAR(100),
    channel VARCHAR(50),
    post_date DATE NOT NULL,
    effective_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_transaction_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT
);

-- Index on account_id and post_date for efficient date-range queries
CREATE INDEX idx_transaction_account_post_date ON transactions(account_id, post_date DESC);

-- Index on reference_number for fast lookups
CREATE INDEX idx_transaction_reference_number ON transactions(reference_number);

-- Index on status for status-based queries
CREATE INDEX idx_transaction_status ON transactions(status);

-- Index on category for category-based filtering
CREATE INDEX idx_transaction_category ON transactions(category);

-- Index on created_at for chronological queries
CREATE INDEX idx_transaction_created_at ON transactions(created_at DESC);

-- Index on account_id alone for quick account-based searches
CREATE INDEX idx_transaction_account_id ON transactions(account_id);
