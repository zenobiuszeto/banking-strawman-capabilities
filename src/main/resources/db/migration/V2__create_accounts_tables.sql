-- Create accounts table
CREATE TABLE accounts (
    id UUID PRIMARY KEY NOT NULL,
    customer_id UUID NOT NULL,
    account_number VARCHAR(12) NOT NULL UNIQUE,
    account_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    current_balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    available_balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    pending_balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    hold_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    interest_rate NUMERIC(5, 4),
    accrued_interest NUMERIC(19, 2),
    overdraft_limit NUMERIC(19, 2),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    opened_date DATE NOT NULL,
    closed_date DATE,
    maturity_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create indexes for accounts table
CREATE INDEX idx_customer_id ON accounts(customer_id);
CREATE INDEX idx_account_number ON accounts(account_number);
CREATE INDEX idx_status ON accounts(status);

-- Create interest_charges table
CREATE TABLE interest_charges (
    id UUID PRIMARY KEY NOT NULL,
    account_id UUID NOT NULL,
    charge_type VARCHAR(50) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    running_balance NUMERIC(19, 2),
    description VARCHAR(500),
    post_date DATE NOT NULL,
    effective_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

-- Create indexes for interest_charges table
CREATE INDEX idx_account_id_post_date ON interest_charges(account_id, post_date);

-- Create balance_history table
CREATE TABLE balance_history (
    id UUID PRIMARY KEY NOT NULL,
    account_id UUID NOT NULL,
    opening_balance NUMERIC(19, 2) NOT NULL,
    closing_balance NUMERIC(19, 2) NOT NULL,
    total_credits NUMERIC(19, 2) NOT NULL,
    total_debits NUMERIC(19, 2) NOT NULL,
    snapshot_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

-- Create indexes for balance_history table
CREATE INDEX idx_account_id_snapshot_date ON balance_history(account_id, snapshot_date);
