-- Create wire_transfers table
CREATE TABLE IF NOT EXISTS wire_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    wire_reference_number VARCHAR(50) NOT NULL UNIQUE,
    fed_reference_number VARCHAR(50),
    wire_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    fee NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    -- Sender Information
    sender_name VARCHAR(100) NOT NULL,
    sender_account_number VARCHAR(50) NOT NULL,
    sender_routing_number VARCHAR(20) NOT NULL,
    sender_bank_name VARCHAR(100) NOT NULL,

    -- Beneficiary Information
    beneficiary_name VARCHAR(100) NOT NULL,
    beneficiary_account_number VARCHAR(50) NOT NULL,
    beneficiary_routing_number VARCHAR(20),
    beneficiary_bank_name VARCHAR(100) NOT NULL,
    beneficiary_bank_address VARCHAR(200),

    -- International Wire Information
    intermediary_bank_name VARCHAR(100),
    intermediary_swift_code VARCHAR(11),
    beneficiary_swift_code VARCHAR(11),
    beneficiary_iban VARCHAR(34),

    -- Additional Information
    purpose_of_wire VARCHAR(200),
    memo VARCHAR(500),
    failure_reason VARCHAR(500),

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_account_id ON wire_transfers(account_id);
CREATE INDEX idx_wire_reference ON wire_transfers(wire_reference_number);
CREATE INDEX idx_status ON wire_transfers(status);
CREATE INDEX idx_created_at ON wire_transfers(created_at);
CREATE INDEX idx_account_status ON wire_transfers(account_id, status);
CREATE INDEX idx_account_created ON wire_transfers(account_id, created_at);
