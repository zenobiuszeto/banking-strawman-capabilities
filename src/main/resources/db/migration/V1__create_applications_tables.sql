-- Create applications table
CREATE TABLE applications (
    id UUID PRIMARY KEY NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL,
    ssn VARCHAR(50) NOT NULL,
    date_of_birth DATE NOT NULL,
    address_line_1 VARCHAR(255) NOT NULL,
    address_line_2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(2) NOT NULL,
    zip_code VARCHAR(10) NOT NULL,
    country VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    requested_account_type VARCHAR(50) NOT NULL,
    referral_code VARCHAR(100),
    rejection_reason TEXT,
    assigned_reviewer_id UUID,
    submitted_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create indexes for applications table
CREATE INDEX idx_email ON applications(email);
CREATE INDEX idx_status ON applications(status);
CREATE INDEX idx_submitted_at ON applications(submitted_at);

-- Create application_documents table
CREATE TABLE application_documents (
    id UUID PRIMARY KEY NOT NULL,
    application_id UUID NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_url TEXT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    verification_status VARCHAR(50) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    CONSTRAINT fk_application_id
        FOREIGN KEY (application_id)
        REFERENCES applications(id)
        ON DELETE CASCADE
);

-- Create indexes for application_documents table
CREATE INDEX idx_application_id ON application_documents(application_id);
CREATE INDEX idx_application_document_type ON application_documents(application_id, document_type);
