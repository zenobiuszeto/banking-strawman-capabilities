-- Create ACH Transfers table
CREATE TABLE IF NOT EXISTS ach_transfers (
    id UUID NOT NULL PRIMARY KEY,
    account_id UUID NOT NULL,
    linked_bank_id UUID NOT NULL,
    trace_number VARCHAR(15) NOT NULL UNIQUE,
    batch_number VARCHAR(10),
    direction VARCHAR(20) NOT NULL,
    ach_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    sec_code VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    sender_name VARCHAR(35) NOT NULL,
    sender_routing_number VARCHAR(9) NOT NULL,
    sender_account_number VARCHAR(17) NOT NULL,
    receiver_name VARCHAR(35) NOT NULL,
    receiver_routing_number VARCHAR(9) NOT NULL,
    receiver_account_number VARCHAR(17) NOT NULL,
    company_name VARCHAR(35) NOT NULL,
    company_id VARCHAR(10) NOT NULL,
    entry_description VARCHAR(10) NOT NULL,
    memo VARCHAR(255),
    effective_date DATE NOT NULL,
    settlement_date DATE,
    return_reason_code VARCHAR(3),
    return_description VARCHAR(255),
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP,
    settled_at TIMESTAMP
);

-- Create indexes for performance optimization
CREATE INDEX IF NOT EXISTS idx_account_id ON ach_transfers(account_id);
CREATE INDEX IF NOT EXISTS idx_trace_number ON ach_transfers(trace_number);
CREATE INDEX IF NOT EXISTS idx_status ON ach_transfers(status);
CREATE INDEX IF NOT EXISTS idx_effective_date ON ach_transfers(effective_date);
CREATE INDEX IF NOT EXISTS idx_batch_number ON ach_transfers(batch_number);

-- Composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_account_id_status ON ach_transfers(account_id, status);
CREATE INDEX IF NOT EXISTS idx_account_id_created_at ON ach_transfers(account_id, created_at);
CREATE INDEX IF NOT EXISTS idx_status_created_at ON ach_transfers(status, created_at);
