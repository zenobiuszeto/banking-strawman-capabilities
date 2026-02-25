-- Create rewards_accounts table
CREATE TABLE rewards_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL UNIQUE,
    tier VARCHAR(20) NOT NULL DEFAULT 'BRONZE',
    total_points_earned BIGINT NOT NULL DEFAULT 0,
    total_points_redeemed BIGINT NOT NULL DEFAULT 0,
    current_balance BIGINT NOT NULL DEFAULT 0,
    lifetime_value NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    tier_expiry_date DATE,
    tier_evaluation_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rewards_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
);

-- Create indexes for rewards_accounts
CREATE INDEX idx_rewards_accounts_customer_id ON rewards_accounts(customer_id);
CREATE INDEX idx_rewards_accounts_tier ON rewards_accounts(tier);


-- Create rewards_transactions table
CREATE TABLE rewards_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rewards_account_id UUID NOT NULL,
    source_transaction_id UUID,
    type VARCHAR(50) NOT NULL,
    points BIGINT NOT NULL,
    running_balance BIGINT NOT NULL,
    description VARCHAR(500),
    reference_code VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rewards_transactions_account FOREIGN KEY (rewards_account_id) REFERENCES rewards_accounts(id) ON DELETE CASCADE
);

-- Create indexes for rewards_transactions
CREATE INDEX idx_rewards_transactions_account_id ON rewards_transactions(rewards_account_id);
CREATE INDEX idx_rewards_transactions_account_created_at ON rewards_transactions(rewards_account_id, created_at DESC);
CREATE INDEX idx_rewards_transactions_source_transaction_id ON rewards_transactions(source_transaction_id);


-- Create rewards_offers table
CREATE TABLE rewards_offers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    offer_code VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    type VARCHAR(50) NOT NULL,
    bonus_points INT NOT NULL DEFAULT 0,
    multiplier DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    min_transaction_amount NUMERIC(19, 2),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    minimum_tier VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for rewards_offers
CREATE INDEX idx_rewards_offers_offer_code ON rewards_offers(offer_code);
CREATE INDEX idx_rewards_offers_active_dates ON rewards_offers(is_active, start_date, end_date);
