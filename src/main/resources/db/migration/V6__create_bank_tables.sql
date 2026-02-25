-- Create linked_banks table
CREATE TABLE IF NOT EXISTS linked_banks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    bank_name VARCHAR(255) NOT NULL,
    routing_number VARCHAR(20) NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    account_holder_name VARCHAR(255),
    nickname VARCHAR(255),
    account_type VARCHAR(50) NOT NULL,
    link_status VARCHAR(50) NOT NULL,
    verification_method VARCHAR(50) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    micro_deposit_1 NUMERIC(10, 2),
    micro_deposit_2 NUMERIC(10, 2),
    verification_attempts INT NOT NULL DEFAULT 0,
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create indexes for linked_banks table
CREATE INDEX idx_linked_banks_customer_id ON linked_banks(customer_id);
CREATE INDEX idx_linked_banks_status ON linked_banks(link_status);
CREATE UNIQUE INDEX idx_linked_banks_customer_routing_account
    ON linked_banks(customer_id, routing_number, account_number)
    WHERE link_status != 'REMOVED';

-- Create bank_directory table
CREATE TABLE IF NOT EXISTS bank_directory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    routing_number VARCHAR(20) NOT NULL UNIQUE,
    bank_name VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(2) NOT NULL,
    zip_code VARCHAR(10),
    phone_number VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Create indexes for bank_directory table
CREATE INDEX idx_bank_directory_routing_number ON bank_directory(routing_number);
CREATE INDEX idx_bank_directory_bank_name ON bank_directory(bank_name);
CREATE INDEX idx_bank_directory_is_active ON bank_directory(is_active);
