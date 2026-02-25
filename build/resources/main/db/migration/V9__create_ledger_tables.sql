-- Create GL accounts (chart of accounts)
CREATE TABLE gl_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    account_type VARCHAR(20) NOT NULL,
    normal_balance VARCHAR(10) NOT NULL,
    parent_id UUID,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gl_accounts_parent FOREIGN KEY (parent_id) REFERENCES gl_accounts(id)
);

CREATE INDEX idx_gl_accounts_code ON gl_accounts(account_code);
CREATE INDEX idx_gl_accounts_type ON gl_accounts(account_type);
CREATE INDEX idx_gl_accounts_parent ON gl_accounts(parent_id);

-- Create journal entries
CREATE TABLE journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_number VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(500) NOT NULL,
    posting_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    reference_id UUID,
    reference_type VARCHAR(50),
    created_by VARCHAR(100),
    reversal_of_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_journal_reversal FOREIGN KEY (reversal_of_id) REFERENCES journal_entries(id)
);

CREATE INDEX idx_journal_entries_number ON journal_entries(entry_number);
CREATE INDEX idx_journal_entries_posting_date ON journal_entries(posting_date);
CREATE INDEX idx_journal_entries_status ON journal_entries(status);
CREATE INDEX idx_journal_entries_reference ON journal_entries(reference_type, reference_id);

-- Create journal entry lines
CREATE TABLE journal_entry_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id UUID NOT NULL,
    gl_account_id UUID NOT NULL,
    account_code VARCHAR(20) NOT NULL,
    debit_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    credit_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_jel_journal_entry FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE CASCADE,
    CONSTRAINT fk_jel_gl_account FOREIGN KEY (gl_account_id) REFERENCES gl_accounts(id)
);

CREATE INDEX idx_jel_journal_entry_id ON journal_entry_lines(journal_entry_id);
CREATE INDEX idx_jel_gl_account_id ON journal_entry_lines(gl_account_id);
CREATE INDEX idx_jel_account_code ON journal_entry_lines(account_code);

-- Create posting rules for automated journal entries
CREATE TABLE posting_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    trigger_event VARCHAR(100) NOT NULL,
    debit_account_code VARCHAR(20) NOT NULL,
    credit_account_code VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_posting_rules_trigger ON posting_rules(trigger_event);
CREATE INDEX idx_posting_rules_code ON posting_rules(rule_code);

-- Seed default chart of accounts
INSERT INTO gl_accounts (id, account_code, name, account_type, normal_balance, active) VALUES
    (gen_random_uuid(), '1000', 'Cash and Cash Equivalents', 'ASSET', 'DEBIT', true),
    (gen_random_uuid(), '1100', 'Customer Deposits', 'ASSET', 'DEBIT', true),
    (gen_random_uuid(), '1200', 'Loans Receivable', 'ASSET', 'DEBIT', true),
    (gen_random_uuid(), '1300', 'Accrued Interest Receivable', 'ASSET', 'DEBIT', true),
    (gen_random_uuid(), '1400', 'Debit Card Receivables', 'ASSET', 'DEBIT', true),
    (gen_random_uuid(), '2000', 'Customer Deposit Liabilities', 'LIABILITY', 'CREDIT', true),
    (gen_random_uuid(), '2100', 'ACH Payable', 'LIABILITY', 'CREDIT', true),
    (gen_random_uuid(), '2200', 'Wire Payable', 'LIABILITY', 'CREDIT', true),
    (gen_random_uuid(), '2300', 'Pending Settlement Payable', 'LIABILITY', 'CREDIT', true),
    (gen_random_uuid(), '3000', 'Retained Earnings', 'EQUITY', 'CREDIT', true),
    (gen_random_uuid(), '4000', 'Interest Income', 'REVENUE', 'CREDIT', true),
    (gen_random_uuid(), '4100', 'Fee Income', 'REVENUE', 'CREDIT', true),
    (gen_random_uuid(), '4200', 'Interchange Income', 'REVENUE', 'CREDIT', true),
    (gen_random_uuid(), '5000', 'Interest Expense', 'EXPENSE', 'DEBIT', true),
    (gen_random_uuid(), '5100', 'Operating Expense', 'EXPENSE', 'DEBIT', true),
    (gen_random_uuid(), '5200', 'Rewards Expense', 'EXPENSE', 'DEBIT', true);

-- Seed default posting rules
INSERT INTO posting_rules (id, rule_code, name, trigger_event, debit_account_code, credit_account_code, active) VALUES
    (gen_random_uuid(), 'DEPOSIT', 'Customer Deposit', 'ACCOUNT_CREATED', '1000', '2000', true),
    (gen_random_uuid(), 'ACH_OUT', 'ACH Outbound Transfer', 'ACH_INITIATED', '2100', '1000', true),
    (gen_random_uuid(), 'WIRE_OUT', 'Wire Outbound Transfer', 'WIRE_INITIATED', '2200', '1000', true),
    (gen_random_uuid(), 'DEBIT_SETTLE', 'Debit Card Settlement', 'DEBIT_SETTLED', '2300', '1000', true),
    (gen_random_uuid(), 'INTEREST_EARN', 'Interest Earned', 'INTEREST_POSTED', '5000', '2000', true),
    (gen_random_uuid(), 'REWARDS_EARN', 'Rewards Points Earned', 'REWARDS_EARNED', '5200', '2000', true);

