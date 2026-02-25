# 🏦 Banking Platform

A production-grade, cloud-native banking backend built with **Spring Boot 3.3**, **Java 21**, and a modern event-driven microservices architecture. This platform covers the full spectrum of digital banking capabilities — from customer onboarding to double-entry accounting, debit card networks, and real-time payment rails.

---

## 📋 Table of Contents

- [Architecture Overview](#architecture-overview)
- [Technology Stack](#technology-stack)
- [Modules & Capabilities](#modules--capabilities)
  - [1. Onboarding](#1-onboarding)
  - [2. Account & Dashboard](#2-account--dashboard)
  - [3. Transactions](#3-transactions)
  - [4. ACH Transfers](#4-ach-transfers)
  - [5. Wire Transfers](#5-wire-transfers)
  - [6. Bank Management](#6-bank-management)
  - [7. Rewards](#7-rewards)
  - [8. Reporting](#8-reporting)
  - [9. Ledger (Double-Entry Accounting)](#9-ledger-double-entry-accounting)
  - [10. Debit Network](#10-debit-network)
- [Shared Infrastructure](#shared-infrastructure)
- [Database Schema](#database-schema)
- [API Reference](#api-reference)
- [Event Streaming (Kafka)](#event-streaming-kafka)
- [Observability](#observability)
- [Getting Started](#getting-started)
- [Running with Docker](#running-with-docker)
- [Configuration](#configuration)
- [Testing](#testing)
- [Project Structure](#project-structure)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Banking Platform API                      │
│                      (Spring Boot 3.3 / Java 21)                │
├──────────┬──────────┬──────────┬──────────┬──────────┬──────────┤
│Onboarding│ Account  │   ACH    │   Wire   │  Debit   │  Ledger  │
│          │Dashboard │ Transfer │ Transfer │ Network  │ (GL/JE)  │
├──────────┴──────────┴──────────┴──────────┴──────────┴──────────┤
│    Bank Mgmt   │  Transactions  │   Rewards   │   Reporting      │
├────────────────┴───────────────┴─────────────┴──────────────────┤
│              Shared: Security · Kafka · Cache · Tracing          │
├──────────────┬──────────────┬───────────────┬────────────────────┤
│  PostgreSQL  │   MongoDB    │     Redis     │       Kafka        │
└──────────────┴──────────────┴───────────────┴────────────────────┘
```

The platform follows a **modular monolith** pattern — each domain is a self-contained package with its own controller, service, mapper, repository, DTOs, entities, and events. This design can be trivially split into microservices at module boundaries.

---

## Technology Stack

| Category | Technology |
|---|---|
| **Framework** | Spring Boot 3.3.0 |
| **Language** | Java 21 (LTS) |
| **Build** | Gradle 8.x with Spring Dependency Management |
| **Primary DB** | PostgreSQL 16 via Spring Data JPA / Hibernate |
| **Document DB** | MongoDB 7 via Spring Data MongoDB |
| **Cache** | Redis 7 via Spring Data Redis |
| **Messaging** | Apache Kafka via Spring Kafka |
| **Migrations** | Flyway (10 versioned migration scripts) |
| **Security** | Spring Security 6 |
| **API Docs** | SpringDoc OpenAPI 3 / Swagger UI |
| **Observability** | Micrometer + Prometheus + OpenTelemetry (OTLP) |
| **Containerisation** | Docker (multi-stage build), Docker Compose |
| **Load Testing** | Gatling |
| **Testing** | JUnit 5, Mockito 5, Testcontainers |
| **Code Gen** | Lombok 1.18.38 |

---

## Modules & Capabilities

### 1. Onboarding

**Package:** `com.banking.platform.onboarding`  
**Base URL:** `POST /api/v1/applications`

Handles the full customer application lifecycle from initial submission through KYC review to account creation.

| Capability | Detail |
|---|---|
| Submit application | Collects PII, address, account type preference, optional referral code |
| Application retrieval | Fetch by ID or list all by status (paginated) |
| Status management | Workflow: `SUBMITTED → KYC_PENDING → KYC_APPROVED → APPROVED → ACCOUNT_CREATED` |
| Document management | Attach/retrieve supporting documents per application |
| Duplicate prevention | Email uniqueness check on submission |
| Event publishing | Kafka events on every status transition |

**Entities:** `Application`, `ApplicationDocument`  
**Statuses:** `SUBMITTED`, `UNDER_REVIEW`, `KYC_PENDING`, `KYC_APPROVED`, `KYC_REJECTED`, `APPROVED`, `REJECTED`, `ACCOUNT_CREATED`  
**Account Types:** `CHECKING`, `SAVINGS`, `MONEY_MARKET`, `CERTIFICATE_OF_DEPOSIT`

---

### 2. Account & Dashboard

**Package:** `com.banking.platform.account`  
**Base URLs:** `/api/v1/accounts`, `/api/v1/dashboard`, `/api/v1/interest-charges`

Core account management with real-time balance tracking, dashboard aggregation, and automated interest accrual.

| Capability | Detail |
|---|---|
| Account creation | Create accounts linked to onboarded customers |
| Balance management | Tracks `currentBalance`, `availableBalance`, `pendingBalance`, `holdAmount` |
| Balance history | Immutable audit trail of all balance changes |
| Account status | `ACTIVE`, `INACTIVE`, `FROZEN`, `CLOSED` |
| Dashboard | Aggregated summary: balances, recent transactions, account highlights |
| Interest charges | Record and retrieve interest charges by date range |
| Cache layer | Redis caching on account lookups (TTL configurable) |
| Kafka events | `account.created`, `account.updated`, `account.status.changed` |

**Entities:** `Account`, `BalanceHistory`, `InterestCharge`  
**Charge Types:** `INTEREST`, `OVERDRAFT_FEE`, `MAINTENANCE_FEE`, `LATE_FEE`

---

### 3. Transactions

**Package:** `com.banking.platform.transaction`  
**Base URL:** `/api/v1/transactions`

Full transaction ledger with advanced search using JPA Specifications, categorisation, and reversal support.

| Capability | Detail |
|---|---|
| Create transaction | Validates amount, type, category, dates; generates unique reference number |
| Retrieve by ID or reference | Lookup by UUID or alphanumeric reference (e.g. `TXN-XXXXXXXXXX-XXXXXXXX`) |
| List by account | Paginated, sorted by creation date descending |
| Advanced search | Multi-criteria filter: type, category, status, amount range, date range, keyword |
| Transaction summary | Aggregated totals (credits, debits, count) over a date range |
| Reversal | Reverse a posted transaction with audit trail |
| Event publishing | Kafka `TransactionEvent` on create and reversal |

**Transaction Types:** `DEBIT`, `CREDIT`, `TRANSFER`, `FEE`, `INTEREST`, `REVERSAL`  
**Categories:** `POS_PURCHASE`, `ATM_WITHDRAWAL`, `ONLINE_PURCHASE`, `TRANSFER_IN`, `TRANSFER_OUT`, `DIRECT_DEPOSIT`, `BILL_PAYMENT`, `WIRE_TRANSFER`, `ACH_TRANSFER`, `REFUND`, `FEE`, `INTEREST`, `OTHER`  
**Statuses:** `PENDING`, `POSTED`, `REVERSED`, `FAILED`

---

### 4. ACH Transfers

**Package:** `com.banking.platform.ach`  
**Base URL:** `/api/v1/ach`

Full ACH payment origination and processing with NACHA-standard SEC codes, batch processing, and returns handling.

| Capability | Detail |
|---|---|
| Initiate ACH | Create debit/credit ACH transfers with routing/account validation |
| Search transfers | Filter by account ID, status, date range, trace number |
| ACH returns | Process returned items with NACHA return reason codes |
| Batch processing | Group transfers into batches with batch number tracking |
| Trace number | Auto-generated unique NACHA trace number per transfer |
| Effective date | Support for same-day and future-dated ACH |
| Kafka events | `ach.initiated`, `ach.processed`, `ach.returned` |

**Transfer Directions:** `CREDIT`, `DEBIT`  
**ACH Types:** `PPD` (Personal), `CCD` (Corporate), `WEB`, `TEL`  
**SEC Codes:** `PPD`, `CCD`, `WEB`, `TEL`, `CTX`, `IAT`  
**Statuses:** `INITIATED`, `PENDING`, `PROCESSING`, `COMPLETED`, `RETURNED`, `FAILED`, `CANCELLED`

---

### 5. Wire Transfers

**Package:** `com.banking.platform.wire`  
**Base URL:** `/api/v1/wires`

Domestic and international wire transfer processing with SWIFT/IBAN support, fee calculation, and full status lifecycle.

| Capability | Detail |
|---|---|
| Initiate wire | Domestic ($25 fee) or international ($45 fee) with full beneficiary details |
| SWIFT/IBAN support | International wires support SWIFT BIC, IBAN, and intermediary bank |
| Status workflow | `INITIATED → PENDING → APPROVED → COMPLETED / FAILED / CANCELLED` |
| Approve wire | Compliance approval step before settlement |
| Complete / Fail wire | Mark settlement outcome with network reference |
| Cancel wire | Cancel pending/initiated wires |
| Reference number | Auto-generated `WIRE-XXXXXXXX-XXXX` format |
| List & search | Paginated list by account; filter by status, type, date |
| Application events | Spring `ApplicationEventPublisher` for wire lifecycle events |

**Wire Types:** `DOMESTIC`, `INTERNATIONAL`  
**Statuses:** `INITIATED`, `PENDING`, `APPROVED`, `COMPLETED`, `FAILED`, `CANCELLED`

---

### 6. Bank Management

**Package:** `com.banking.platform.bank`  
**Base URL:** `/api/v1/banks`

External bank account linking and verification for ACH and wire origination.

| Capability | Detail |
|---|---|
| Link bank | Add checking/savings accounts from external institutions |
| Routing number lookup | Validate against internal bank directory |
| Micro-deposit verification | Send two micro-deposits; customer confirms amounts to verify |
| Max attempts | 3 verification attempts before lockout |
| Set primary | Designate a verified bank as the default funding source |
| Bank directory search | Search by name (paginated) |
| Verification methods | `MICRO_DEPOSIT`, `INSTANT_VERIFICATION` |

**Entities:** `LinkedBank`, `BankDirectory`  
**Link Statuses:** `PENDING_VERIFICATION`, `VERIFIED`, `FAILED`, `REMOVED`  
**Account Types:** `CHECKING`, `SAVINGS`

---

### 7. Rewards

**Package:** `com.banking.platform.rewards`  
**Base URL:** `/api/v1/rewards`

Full-featured rewards program with points earning, tiered multipliers, redemptions, and merchant offers.

| Capability | Detail |
|---|---|
| Enrol customer | Create rewards account with tier assignment |
| Earn points | Credit points with tier-based multipliers; auto-tier upgrade |
| Redeem points | Convert points to cash-back, gift cards, travel, or merchandise |
| Tier system | `BRONZE → SILVER → GOLD → PLATINUM` with configurable thresholds |
| Transaction history | Paginated earn/redeem history with type filtering |
| Rewards summary | Balance, tier, lifetime points, YTD earnings |
| Active offers | Merchant-specific bonus-point campaigns |
| Redemption tracking | Full status lifecycle with approval workflow |
| Kafka events | Points earned, redeemed, tier upgraded |

**Tiers:** `BRONZE` (1×), `SILVER` (1.25×), `GOLD` (1.5×), `PLATINUM` (2×)  
**Redemption Types:** `CASH_BACK`, `GIFT_CARD`, `TRAVEL`, `MERCHANDISE`, `STATEMENT_CREDIT`  
**Offer Types:** `BONUS_POINTS`, `CASH_BACK`, `DISCOUNT`, `STATEMENT_CREDIT`

---

### 8. Reporting

**Package:** `com.banking.platform.reporting`  
**Base URL:** `/api/v1/reports`

On-demand and scheduled report generation for statements and activity summaries.

| Capability | Detail |
|---|---|
| Generate report | On-demand creation for a date range; async-ready |
| Report types | `ACCOUNT_STATEMENT`, `TRANSACTION_HISTORY`, `ACH_ACTIVITY`, `WIRE_ACTIVITY`, `REWARDS_SUMMARY`, `TAX_STATEMENT` |
| Report statuses | `QUEUED → PROCESSING → COMPLETED / FAILED` |
| Retrieve report | Fetch by ID with download URL |
| Customer reports | List all reports for a customer (paginated) |
| Scheduled reports | Create recurring reports on daily/weekly/monthly/quarterly/annual schedule |
| Toggle scheduled | Activate/deactivate recurring report schedules |
| Delete scheduled | Remove a scheduled report |
| Kafka events | `report.generated`, `report.failed` |

**Frequencies:** `DAILY`, `WEEKLY`, `MONTHLY`, `QUARTERLY`, `ANNUALLY`

---

### 9. Ledger (Double-Entry Accounting)

**Package:** `com.banking.platform.ledger`  
**Base URL:** `/api/v1/ledger`

A complete double-entry General Ledger (GL) with journal entries, posting rules, and trial balance generation.

| Capability | Detail |
|---|---|
| Chart of accounts | Create and manage GL accounts with type and normal balance |
| GL account types | `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE` |
| Normal balance | `DEBIT` or `CREDIT` side convention per account |
| Journal entries | Multi-line journal entries with balanced debits/credits validation |
| Entry workflow | `DRAFT → POSTED / VOIDED`; reversals create counter-entries |
| Auto-numbering | Prefix-based sequential entry numbers (e.g. `JE-2024-00001`) |
| Reverse entry | Create reversing journal entry linked to the original |
| Trial balance | Aggregate debit/credit totals per account from posted entries |
| GL account balance | Current running balance per GL account |
| Posting rules | Configurable rules for automated journal entry generation |
| Reference linking | Tie journal entries to source transactions via `referenceType`/`referenceId` |
| Kafka events | `journal.posted`, `journal.reversed` |

**GL Account Types:** `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE`  
**Entry Statuses:** `DRAFT`, `POSTED`, `VOIDED`, `REVERSED`

---

### 10. Debit Network

**Package:** `com.banking.platform.debitnetwork`  
**Base URL:** `/api/v1/debit-network`

End-to-end debit card lifecycle management — from issuance to real-time authorization and network settlement.

| Capability | Detail |
|---|---|
| Issue card | Issue debit card for an active account with configurable daily/monthly spending limits |
| Card masking | Card number stored masked (`**** **** **** 1234`); 3-year expiry |
| Block / Unblock | Freeze/unfreeze card instantly |
| Update limits | Adjust daily and monthly spending limits |
| Authorize transaction | Real-time spend authorisation with multi-reason decline logic |
| Decline reasons | `INSUFFICIENT_FUNDS`, `CARD_BLOCKED`, `CARD_EXPIRED`, `DAILY_LIMIT_EXCEEDED`, `MONTHLY_LIMIT_EXCEEDED`, `INVALID_CARD`, `SUSPECTED_FRAUD`, `MERCHANT_NOT_ALLOWED` |
| Limit enforcement | Daily and monthly spend counters with automatic midnight/month reset |
| Balance holds | Places hold on account available balance during authorization |
| Transaction history | Paginated history by card or by account |
| Batch settlement | Settle all `AUTHORIZED` transactions, release holds, debit account |
| Reversal | Reverse authorized or settled transactions; funds returned to account |
| Settlement records | Track settlement batches with `BATCH-YYYYMMDD-XXXXXXXX` IDs |
| Kafka events | `card.issued`, `card.blocked`, `transaction.authorized`, `transaction.declined`, `settlement.completed` |

**Card Statuses:** `ACTIVE`, `BLOCKED`, `EXPIRED`, `CANCELLED`, `PENDING_ACTIVATION`  
**Transaction Types:** `PURCHASE`, `ATM_WITHDRAWAL`, `REFUND`, `REVERSAL`, `CASH_BACK`  
**Transaction Statuses:** `AUTHORIZED`, `SETTLED`, `DECLINED`, `REVERSED`, `EXPIRED`

---

## Shared Infrastructure

### Security (`com.banking.platform.shared.config.SecurityConfig`)
- Spring Security 6 filter chain
- Actuator endpoints publicly accessible for health probes
- All other endpoints require authentication

### Exception Handling (`com.banking.platform.shared.exception`)
Global `@RestControllerAdvice` maps domain exceptions to standardised RFC 7807-style error responses:

| Exception | HTTP Status |
|---|---|
| `ResourceNotFoundException` | 404 Not Found |
| `EntityNotFoundException` | 404 Not Found |
| `DuplicateResourceException` | 409 Conflict |
| `InsufficientFundsException` | 422 Unprocessable Entity |
| `ValidationException` | 400 Bad Request |
| `BusinessException` | 422 Unprocessable Entity |
| `ConstraintViolationException` | 400 Bad Request |
| `MethodArgumentNotValidException` | 400 Bad Request |

### Correlation ID Filter (`com.banking.platform.shared.filter.CorrelationIdFilter`)
- Injects `X-Correlation-ID` header into every request/response
- Propagates through MDC for distributed tracing

### Caching (Redis)
- Account lookups: `@Cacheable("accounts")`
- Debit card lists: `@Cacheable("debit-cards")`
- Cache eviction on mutations via `@CacheEvict`
- Default TTL: 10 minutes

### Kafka Configuration (`com.banking.platform.shared.config.KafkaConfig`)

| Topic | Published By |
|---|---|
| `banking.application.events` | Onboarding |
| `banking.account.events` | Account service |
| `banking.transactions` | Transaction service |
| `banking.ach.events` | ACH service |
| `banking.wire.events` | Wire service (via ApplicationEventPublisher) |
| `banking.rewards.events` | Rewards service |
| `banking.ledger.events` | Ledger service |
| `banking.report.events` | Reporting service |
| `debit-network-events` | Debit Network service |

All topics configured with 3 partitions, replication factor 1 (configurable), idempotent producers (`acks=all`), and a Dead Letter Topic (DLT) via `DefaultErrorHandler` with `FixedBackOff(1000ms, 3 retries)`.

### Pagination (`com.banking.platform.shared.util.PagedResponse<T>`)
All paginated endpoints return a standard envelope:
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "last": false
}
```

---

## Database Schema

Flyway manages 10 versioned migrations:

| Migration | Tables Created |
|---|---|
| `V1` | `applications`, `application_documents` |
| `V2` | `accounts`, `balance_history`, `interest_charges` |
| `V3` | `transactions` |
| `V4` | `ach_transfers` |
| `V5` | `wire_transfers` |
| `V6` | `linked_banks`, `bank_directory` |
| `V7` | `rewards_accounts`, `rewards_tiers`, `rewards_transactions`, `rewards_redemptions`, `rewards_offers` |
| `V8` | `reports`, `scheduled_reports` |
| `V9` | `gl_accounts`, `journal_entries`, `journal_entry_lines`, `posting_rules` |
| `V10` | `debit_cards`, `debit_transactions`, `network_settlements` |

All tables use:
- `UUID` primary keys (PostgreSQL `uuid` type)
- `created_at` / `updated_at` timestamps managed via JPA lifecycle hooks
- Strategic composite indexes on foreign keys and filter columns
- `ENUM` columns for status/type fields

---

## API Reference

The full interactive API documentation is available at runtime via **Swagger UI**:

```
http://localhost:8080/api/swagger-ui.html
http://localhost:8080/api/v3/api-docs
```

### Endpoint Summary

| Module | Method | Path | Description |
|---|---|---|---|
| **Onboarding** | POST | `/api/v1/applications` | Submit new application |
| | GET | `/api/v1/applications/{id}` | Get application by ID |
| | GET | `/api/v1/applications` | List applications by status |
| | PATCH | `/api/v1/applications/{id}/status` | Update application status |
| | POST | `/api/v1/applications/{id}/documents` | Upload document |
| **Account** | POST | `/api/v1/accounts` | Create account |
| | GET | `/api/v1/accounts/{id}` | Get account |
| | PATCH | `/api/v1/accounts/{id}/status` | Update account status |
| | GET | `/api/v1/dashboard/{customerId}` | Get customer dashboard |
| | GET | `/api/v1/interest-charges/{accountId}` | List interest charges |
| **Transactions** | POST | `/api/v1/transactions` | Create transaction |
| | GET | `/api/v1/transactions/{id}` | Get transaction |
| | GET | `/api/v1/transactions/reference/{ref}` | Get by reference |
| | GET | `/api/v1/transactions/account/{accountId}` | List by account |
| | POST | `/api/v1/transactions/search` | Advanced search |
| | GET | `/api/v1/transactions/account/{accountId}/summary` | Get summary |
| | POST | `/api/v1/transactions/{id}/reverse` | Reverse transaction |
| **ACH** | POST | `/api/v1/ach` | Initiate ACH transfer |
| | GET | `/api/v1/ach/{id}` | Get ACH transfer |
| | GET | `/api/v1/ach/account/{accountId}` | List by account |
| | POST | `/api/v1/ach/search` | Search ACH transfers |
| | POST | `/api/v1/ach/{id}/return` | Process return |
| **Wire** | POST | `/api/v1/wires` | Initiate wire transfer |
| | GET | `/api/v1/wires/{id}` | Get wire transfer |
| | GET | `/api/v1/wires/account/{accountId}` | List by account |
| | POST | `/api/v1/wires/{id}/approve` | Approve wire |
| | POST | `/api/v1/wires/{id}/complete` | Complete wire |
| | POST | `/api/v1/wires/{id}/fail` | Fail wire |
| | POST | `/api/v1/wires/{id}/cancel` | Cancel wire |
| **Banks** | POST | `/api/v1/banks/link` | Link bank account |
| | GET | `/api/v1/banks/{customerId}` | Get linked banks |
| | POST | `/api/v1/banks/{id}/verify` | Verify micro-deposits |
| | POST | `/api/v1/banks/{id}/primary` | Set as primary |
| | DELETE | `/api/v1/banks/{id}` | Remove linked bank |
| | GET | `/api/v1/banks/directory/search` | Search bank directory |
| **Rewards** | POST | `/api/v1/rewards/enrol` | Enrol customer |
| | GET | `/api/v1/rewards/{customerId}` | Get rewards account |
| | GET | `/api/v1/rewards/{customerId}/summary` | Get summary |
| | POST | `/api/v1/rewards/earn` | Earn points |
| | POST | `/api/v1/rewards/redeem` | Redeem points |
| | GET | `/api/v1/rewards/{customerId}/transactions` | Transaction history |
| | GET | `/api/v1/rewards/{customerId}/offers` | Active offers |
| **Reporting** | POST | `/api/v1/reports` | Generate report |
| | GET | `/api/v1/reports/{reportId}` | Get report |
| | GET | `/api/v1/reports/customer/{customerId}` | Customer reports |
| | POST | `/api/v1/reports/scheduled` | Create scheduled report |
| | GET | `/api/v1/reports/scheduled/customer/{customerId}` | Scheduled reports |
| | PATCH | `/api/v1/reports/scheduled/{id}/toggle` | Activate/deactivate |
| | DELETE | `/api/v1/reports/scheduled/{id}` | Delete scheduled |
| **Ledger** | POST | `/api/v1/ledger/accounts` | Create GL account |
| | GET | `/api/v1/ledger/accounts` | Chart of accounts |
| | GET | `/api/v1/ledger/accounts/{id}` | Get GL account |
| | GET | `/api/v1/ledger/accounts/code/{code}` | Get by code |
| | POST | `/api/v1/ledger/journal-entries` | Create journal entry |
| | GET | `/api/v1/ledger/journal-entries/{id}` | Get journal entry |
| | GET | `/api/v1/ledger/journal-entries` | List journal entries |
| | POST | `/api/v1/ledger/journal-entries/{id}/post` | Post draft entry |
| | POST | `/api/v1/ledger/journal-entries/{id}/reverse` | Reverse entry |
| | GET | `/api/v1/ledger/trial-balance` | Generate trial balance |
| | POST | `/api/v1/ledger/posting-rules` | Create posting rule |
| | GET | `/api/v1/ledger/posting-rules` | List posting rules |
| | PATCH | `/api/v1/ledger/posting-rules/{id}/toggle` | Toggle rule active |
| **Debit Network** | POST | `/api/v1/debit-network/cards` | Issue debit card |
| | GET | `/api/v1/debit-network/cards/{cardId}` | Get card |
| | GET | `/api/v1/debit-network/cards/customer/{customerId}` | Customer cards |
| | POST | `/api/v1/debit-network/cards/{cardId}/block` | Block card |
| | POST | `/api/v1/debit-network/cards/{cardId}/unblock` | Unblock card |
| | PATCH | `/api/v1/debit-network/cards/{cardId}/limits` | Update limits |
| | POST | `/api/v1/debit-network/authorize` | Authorize transaction |
| | GET | `/api/v1/debit-network/transactions/card/{cardId}` | Card transactions |
| | GET | `/api/v1/debit-network/transactions/account/{accountId}` | Account transactions |
| | POST | `/api/v1/debit-network/transactions/{id}/reverse` | Reverse transaction |
| | POST | `/api/v1/debit-network/settlements/batch` | Batch settle |
| | GET | `/api/v1/debit-network/settlements/{id}` | Get settlement |
| | GET | `/api/v1/debit-network/settlements` | List settlements |

---

## Event Streaming (Kafka)

Every domain publishes strongly-typed events to Kafka. All events include:
- `eventId` — UUID
- `eventType` — dot-notation string (e.g. `transaction.created`)
- `timestamp` — `Instant`
- Domain-specific payload fields

Consumers can subscribe to any topic independently, enabling downstream systems (analytics, notifications, audit, fraud detection) to react without coupling to the core platform.

---

## Observability

### Health & Readiness Probes
```
GET /api/actuator/health/liveness
GET /api/actuator/health/readiness
```

### Metrics (Prometheus)
```
GET /api/actuator/metrics
GET /api/actuator/prometheus
```
All metrics tagged with `application=banking-platform`.

### Distributed Tracing (OpenTelemetry)
- Micrometer Tracing bridge to OpenTelemetry
- OTLP exporter configured for OpenTelemetry Collector
- Traces include `X-Correlation-ID` propagated from `CorrelationIdFilter`
- Collector config: `infra/otel-collector-config.yml`

### Prometheus Scrape Config
- Config: `infra/prometheus.yml`

---

## Getting Started

> **Architecture & Implementation Plan:** See [ARCHITECTURE.md](./ARCHITECTURE.md) for the full design covering all 20+ technology layers including Temporal workflows, Resilience4j, Vault, Consul, Kubernetes, Terraform, and more.

### Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Java | 21+ | Runtime & build |
| Docker & Docker Compose | 24+ | Local infra & containers |
| Gradle | 8.x (wrapper included) | Build tool |
| kubectl | 1.29+ | Kubernetes deployment |
| Helm | 3.14+ | K8s package manager |
| Terraform | 1.8+ | Infrastructure provisioning |
| AWS CLI | 2.x | EKS + AWS resource management |
| Vault CLI | 1.16+ | Secrets management (optional locally) |
| Consul CLI | 1.18+ | Config management (optional locally) |
| Temporal CLI | 0.12+ | Workflow engine (optional locally) |

### Build

```bash
# Build jar (skip tests)
./gradlew build -x test

# Build and run tests
./gradlew build

# Run only unit tests
./gradlew test

# View test report
open build/reports/tests/test/index.html

# Run only integration tests (requires Docker for Testcontainers)
./gradlew integrationTest
```

### Run Locally (with infrastructure)

```bash
# 1. Start all infrastructure
docker-compose up -d postgres mongo redis kafka keycloak vault consul temporal

# 2. Wait for services to be healthy (check with)
docker-compose ps

# 3. Initialize Vault (first time only)
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=root
bash infra/vault/init-vault.sh

# 4. Load Consul config (first time only)
export CONSUL_HTTP_ADDR=http://localhost:8500
bash infra/consul/consul-register.sh

# 5. Run the application
./gradlew bootRun --args='--spring.profiles.active=local'
```

The application starts on **http://localhost:8080**.
- Swagger UI: http://localhost:8080/api/swagger-ui.html
- Health:      http://localhost:8080/api/actuator/health
- Metrics:     http://localhost:8080/api/actuator/prometheus

### Obtaining a JWT Token (OAuth2 / Keycloak)

```bash
# Get a token from the local Keycloak instance
TOKEN=$(curl -s -X POST \
  http://localhost:8090/realms/banking/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=banking-app&client_secret=secret&grant_type=password&username=testuser&password=password" \
  | jq -r '.access_token')

# Use the token in API calls
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/accounts/YOUR_ACCOUNT_ID
```

---

## Running with Docker

### Full Stack

```bash
# Build and start everything including the app
docker-compose up --build
```

### Services Started

| Service | Port | Purpose |
|---|---|---|
| `app` | 8080 | Banking Platform API |
| `postgres` | 5432 | Primary relational database |
| `mongo` | 27017 | Document store |
| `redis` | 6379 | Cache / session |
| `kafka` | 9092 | Event streaming |
| `prometheus` | 9090 | Metrics scraping |
| `otel-collector` | 4317 | Trace collection (OTLP/gRPC) |

### Docker Image
The `Dockerfile` uses a two-stage build:
1. **Builder**: `eclipse-temurin:21-jdk` — compiles and packages the JAR
2. **Runtime**: `eclipse-temurin:21-jre-alpine` — minimal JRE with `dumb-init`

Runtime optimisations:
- ZGC garbage collector with generational mode (`-XX:+UseZGC -XX:+ZGenerational`)
- Exploded JAR for faster startup
- Non-root user (`appuser:1000`)
- Graceful shutdown with 30s timeout

---

## Configuration

### Profiles

| Profile | Usage |
|---|---|
| `default` | Base configuration |
| `local` | Local development overrides (`application-local.yml`) |
| `docker` | Container environment (env vars from `docker-compose.yml`) |
| `test` | Test configuration (`application-test.yml` — H2 in-memory) |

### Key Properties

```yaml
# Server
server.port: 8080
server.servlet.context-path: /api

# Database
spring.datasource.url: jdbc:postgresql://localhost:5432/banking_db
spring.jpa.hibernate.ddl-auto: validate  # Flyway owns schema

# Cache
spring.cache.type: redis
spring.cache.redis.time-to-live: 600000  # 10 minutes

# Kafka
spring.kafka.bootstrap-servers: localhost:9092
spring.kafka.producer.acks: all
spring.kafka.producer.properties.enable.idempotence: true

# Actuator
management.endpoints.web.exposure.include: health,info,metrics,prometheus
```

---

## Testing

### Test Suite — 85 Unit Tests ✅ All Passing

| Test Class | Module | Tests |
|---|---|---|
| `AccountServiceTest` | Account | 8 |
| `ApplicationServiceTest` | Onboarding | 10 |
| `AchServiceTest` | ACH | 9 |
| `BankServiceTest` | Bank Management | 11 |
| `TransactionServiceTest` | Transactions | 10 |
| `WireServiceTest` | Wire | 9 |
| `RewardsServiceTest` | Rewards | 10 |
| `LedgerServiceTest` | Ledger | 8 |
| `ReportingServiceTest` | Reporting | 6 |
| `DebitCardServiceTest` | Debit Network | 4 |

### Test Strategy
- **Unit tests**: Mockito mocks all dependencies; no Spring context loaded
- **Test isolation**: `@ExtendWith(MockitoExtension.class)` — fast and lightweight
- **Integration tests**: Testcontainers spin up real PostgreSQL, MongoDB, Kafka
- **Load tests**: Gatling simulations in `gatling/src/gatling/java/simulations/`

### Run Tests

```bash
# All unit tests
./gradlew test

# View test report
open build/reports/tests/test/index.html

# Integration tests (requires Docker)
./gradlew integrationTest

# Load test (Gatling)
./gradlew gatlingRun
```

---

## Project Structure

```
banking-platform/
├── src/
│   ├── main/
│   │   ├── java/com/banking/platform/
│   │   │   ├── BankingPlatformApplication.java
│   │   │   ├── account/           # Account, Dashboard, Interest
│   │   │   │   ├── controller/
│   │   │   │   ├── mapper/
│   │   │   │   ├── model/
│   │   │   │   │   ├── dto/       # Request/Response records
│   │   │   │   │   ├── entity/    # JPA entities
│   │   │   │   │   └── event/     # Kafka event POJOs
│   │   │   │   ├── repository/
│   │   │   │   └── service/
│   │   │   ├── ach/               # ACH Transfers
│   │   │   ├── bank/              # Bank Management
│   │   │   ├── debitnetwork/      # Debit Cards & Settlement
│   │   │   ├── ledger/            # GL / Journal Entries
│   │   │   ├── onboarding/        # Customer Applications
│   │   │   ├── reporting/         # Reports & Scheduling
│   │   │   ├── rewards/           # Points & Redemptions
│   │   │   ├── shared/
│   │   │   │   ├── config/        # Kafka, Redis, Security, OpenAPI
│   │   │   │   ├── exception/     # Domain exceptions + GlobalHandler
│   │   │   │   ├── filter/        # CorrelationIdFilter
│   │   │   │   ├── model/         # Shared DTOs
│   │   │   │   └── util/          # PagedResponse<T>
│   │   │   ├── transaction/       # Transaction Ledger
│   │   │   └── wire/              # Wire Transfers
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       └── db/migration/
│   │           ├── V1__create_applications_tables.sql
│   │           ├── V2__create_accounts_tables.sql
│   │           ├── V3__create_transactions_table.sql
│   │           ├── V4__create_ach_transfers_table.sql
│   │           ├── V5__create_wire_transfers_table.sql
│   │           ├── V6__create_bank_tables.sql
│   │           ├── V7__create_rewards_tables.sql
│   │           ├── V8__create_reporting_tables.sql
│   │           ├── V9__create_ledger_tables.sql
│   │           └── V10__create_debit_network_tables.sql
│   └── test/
│       └── java/com/banking/platform/
│           ├── account/AccountServiceTest.java
│           ├── ach/AchServiceTest.java
│           ├── bank/BankServiceTest.java
│           ├── debitnetwork/DebitCardServiceTest.java
│           ├── ledger/LedgerServiceTest.java
│           ├── onboarding/service/ApplicationServiceTest.java
│           ├── reporting/ReportingServiceTest.java
│           ├── rewards/RewardsServiceTest.java
│           ├── transaction/service/TransactionServiceTest.java
│           └── wire/WireServiceTest.java
├── gatling/                       # Gatling load test simulations
├── infra/
│   ├── otel-collector-config.yml
│   └── prometheus.yml
├── Dockerfile                     # Multi-stage Docker build
├── docker-compose.yml             # Full stack compose
├── build.gradle                   # Gradle build config
└── README.md
```

---

## Kubernetes Deployment

### Prerequisites
```bash
# Authenticate to EKS cluster (staging example)
aws eks update-kubeconfig --name banking-platform-staging --region us-east-1

# Verify connection
kubectl get nodes
```

### First-Time Setup
```bash
# 1. Create namespace
kubectl apply -f infra/k8s/namespace.yml

# 2. Deploy Vault + Consul via Helm (or use Terraform)
helm repo add hashicorp https://helm.releases.hashicorp.com
helm install vault hashicorp/vault -n infra --create-namespace
helm install consul hashicorp/consul -n infra

# 3. Initialize Vault in Kubernetes
kubectl exec -it vault-0 -n infra -- vault operator init
# → Save the unseal keys and root token securely in a password manager

# 4. Apply Vault policy and K8s auth
kubectl exec -it vault-0 -n infra -- sh
# Inside the pod:
vault policy write banking-platform /vault/policies/banking-policy.hcl
vault auth enable kubernetes
# (see infra/vault/init-vault.sh for full setup)
```

### Deploy the App
```bash
# Apply all Kubernetes manifests
kubectl apply -f infra/k8s/namespace.yml
kubectl apply -f infra/k8s/configmap.yml
kubectl apply -f infra/k8s/deployment.yml
kubectl apply -f infra/k8s/service.yml
kubectl apply -f infra/k8s/ingress.yml
kubectl apply -f infra/k8s/hpa.yml
kubectl apply -f infra/k8s/network-policy.yml

# Or apply the whole directory at once
kubectl apply -f infra/k8s/

# Verify rollout
kubectl rollout status deployment/banking-platform -n banking

# Watch pods
kubectl get pods -n banking -w
```

### Update Image
```bash
# Rolling update to a new image
kubectl set image deployment/banking-platform \
  banking-platform=ghcr.io/your-org/banking-platform:NEW_TAG \
  -n banking

# Rollback if needed
kubectl rollout undo deployment/banking-platform -n banking
```

### Scaling
```bash
# Manual scale
kubectl scale deployment banking-platform --replicas=5 -n banking

# HPA scales automatically based on CPU/memory/RPS thresholds (min 3, max 10)
kubectl get hpa -n banking
```

### Logs & Debugging
```bash
# Stream logs from all pods
kubectl logs -f -l app=banking-platform -n banking --max-log-requests=10

# Exec into a pod
kubectl exec -it $(kubectl get pod -l app=banking-platform -n banking -o jsonpath='{.items[0].metadata.name}') \
  -n banking -- sh

# Port-forward for local debugging
kubectl port-forward service/banking-platform-service 8080:80 -n banking
```

---

## Terraform Infrastructure

### Setup
```bash
cd infra/terraform

# 1. Initialize (downloads providers, configures S3 backend)
terraform init

# 2. Create a terraform.tfvars file (gitignored)
cat > terraform.tfvars <<EOF
environment    = "staging"
aws_region     = "us-east-1"
db_password    = "YOUR_SECURE_PASSWORD"
EOF

# 3. Plan — review changes before applying
terraform plan -var-file="terraform.tfvars"

# 4. Apply infrastructure
terraform apply -var-file="terraform.tfvars"

# View outputs (endpoints, cluster name, etc.)
terraform output
```

### Destroy (staging only — production has prevent_destroy locks)
```bash
terraform destroy -var-file="terraform.tfvars" -target=module.eks
```

### Remote State
The Terraform state is stored in S3 (`banking-platform-terraform-state` bucket) with DynamoDB locking. Create these resources manually before first `terraform init`:
```bash
aws s3api create-bucket --bucket banking-platform-terraform-state --region us-east-1
aws dynamodb create-table \
  --table-name banking-platform-tf-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

---

## CI/CD Pipeline

### GitHub Actions Secrets Required
Set these in your GitHub repository under Settings → Secrets:

| Secret | Description |
|---|---|
| `AWS_ACCESS_KEY_ID` | AWS credentials for EKS deploy |
| `AWS_SECRET_ACCESS_KEY` | AWS credentials |
| `AWS_REGION` | e.g. `us-east-1` |
| `SLACK_WEBHOOK_URL` | Deployment notifications |

### Pipeline Stages

**CI (on every push/PR):**
1. Compile & Checkstyle
2. Unit tests (JUnit 5 + Mockito)
3. Integration tests (Testcontainers — real PostgreSQL, MongoDB, Kafka)
4. OWASP Dependency Check + SpotBugs SAST
5. Docker build (multi-platform: amd64 + arm64)
6. Push to GHCR
7. Trivy container vulnerability scan

**CD (on merge to main):**
1. Deploy to staging (rolling update)
2. Smoke test (health endpoint + Gatling smoke simulation)
3. Manual approval gate (GitHub Environment protection rules)
4. Deploy to production (rolling update)
5. Production smoke test
6. Automatic rollback on failure + Slack notification

### Manual Deploy Trigger
```bash
# Trigger CD pipeline with specific image tag
gh workflow run cd.yml \
  -f environment=staging \
  -f image-tag=sha-abc1234
```

---

## Vault Secrets Management

### Local Development
```bash
# Start Vault in dev mode (data is in-memory, auto-unsealed)
docker-compose up -d vault

# Set env vars
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=root          # dev mode root token

# Verify
vault status

# Write a secret
vault kv put secret/banking-platform/db username=banking_user password=banking_password

# Read a secret
vault kv get secret/banking-platform/db
```

### Initialize Production Vault
```bash
chmod +x infra/vault/init-vault.sh
POSTGRES_HOST=your-rds-endpoint \
POSTGRES_ADMIN_USER=banking_admin \
POSTGRES_ADMIN_PASSWORD=your-password \
bash infra/vault/init-vault.sh
```

---

## Consul Dynamic Config

### Local Development
```bash
docker-compose up -d consul

# Upload config
export CONSUL_HTTP_ADDR=http://localhost:8500
consul kv put config/banking-platform/data @infra/consul/banking-platform-config.yml

# Verify
consul kv get config/banking-platform/data

# Watch for changes (triggers @RefreshScope in Spring)
consul watch -type=key -key=config/banking-platform/data echo
```

### Hot Reload a Config Value
```bash
# Change wire daily limit at runtime — no app restart needed
consul kv put config/banking-platform/banking.wire.daily-limit 75000

# Spring Cloud Bus propagates the change to all instances via Kafka
# @RefreshScope beans are automatically re-initialized
```

---

## Performance Testing — Gatling

```bash
# Run full load test against local stack
./gradlew gatlingRun

# Run against staging
./gradlew gatlingRun -Dgatling.baseUrl=https://staging.bankingplatform.com

# Run smoke test only (fast, low load)
./gradlew gatlingRun \
  -Dgatling.simulationClass=simulations.SmokeTestSimulation

# Open the HTML report
open build/reports/gatling/bankingplatformsimulation-*/index.html
```

**Default assertions (fail build if not met):**
- P95 response time < 500ms
- 99.5% successful requests
- Peak load: 200 concurrent users, 10-minute sustained

---

## Monitoring & Observability

### Prometheus & Grafana
```bash
docker-compose up -d prometheus grafana

# Prometheus
open http://localhost:9090

# Grafana (admin / admin)
open http://localhost:3000
```

**Key Grafana dashboards (import from `infra/grafana/`):**
- `banking-overview.json` — TPS, error rate, P99 latency, active accounts
- `jvm-metrics.json` — Heap, GC pauses, virtual threads
- `resilience4j.json` — Circuit breaker states, retry counts, bulkhead usage

### OpenTelemetry Tracing
```bash
docker-compose up -d otel-collector jaeger

# Jaeger UI
open http://localhost:16686
```

### Distributed Logs (Loki + Grafana)
All logs are emitted as structured JSON (via `logback-spring.xml`) and shipped to Loki via Promtail. Query in Grafana with LogQL:
```logql
{app="banking-platform"} | json | level="ERROR"
{app="banking-platform"} | json | correlationId="your-correlation-id"
```

### Useful Actuator Endpoints
```bash
BASE=http://localhost:8080/api/actuator

curl $BASE/health           # full health with component detail
curl $BASE/health/liveness  # K8s liveness probe
curl $BASE/health/readiness # K8s readiness probe
curl $BASE/metrics          # all metric names
curl $BASE/prometheus       # Prometheus scrape endpoint
curl $BASE/info             # build + git info
curl $BASE/threaddump       # virtual thread dump
curl $BASE/heapdump         # JVM heap dump (download .hprof)
```

---

## Profiling

### Flamegraph with Async Profiler (in-pod)
```bash
# 1. Get a shell in the running pod
kubectl exec -it $(kubectl get pod -l app=banking-platform -n banking -o name | head -1) \
  -n banking -- sh

# 2. Inside the pod — run a 60-second CPU profile
wget -q https://github.com/async-profiler/async-profiler/releases/latest/download/async-profiler-linux-x64.tar.gz
tar -xzf async-profiler-linux-x64.tar.gz
./profiler.sh -d 60 -f /tmp/flamegraph.html $(pgrep java)

# 3. Copy the flamegraph to your local machine
kubectl cp banking/$(kubectl get pod -l app=banking-platform -n banking -o jsonpath='{.items[0].metadata.name}'):/tmp/flamegraph.html ./flamegraph.html
open flamegraph.html
```

### JVM Flight Recorder (JFR)
```bash
# Start continuous JFR recording
kubectl exec -it BANKING_POD -n banking -- \
  jcmd 1 JFR.start settings=profile filename=/tmp/recording.jfr duration=120s

# Copy and open in JDK Mission Control
kubectl cp banking/BANKING_POD:/tmp/recording.jfr ./recording.jfr
jmc -open recording.jfr
```

---

## Technology Quick Reference

| Technology | Package / Location | Purpose |
|---|---|---|
| MongoDB | `shared/mongo`, `shared/webhook` | Audit logs, webhook subscriptions |
| Redis Cache | `shared/config/CacheConfig.java` | Account/card L2 cache, rate limiting |
| Spring Batch | `shared/config/BatchConfig.java` | EOD settlement, interest, tier review |
| CloudEvents | `shared/webhook/WebhookEventPublisher.java` | Partner webhook delivery |
| Temporal | `shared/config/TemporalConfig.java`, `shared/saga/` | Durable workflows, saga orchestration |
| Virtual Threads | `shared/config/AsyncConfig.java` | Java 21 Project Loom async |
| Resilience4j | `shared/config/Resilience4jConfig.java`, `application.yml` | CB, bulkhead, retry |
| Saga Pattern | `shared/saga/TransactionSagaWorkflow*.java` | Distributed transaction compensation |
| OAuth2 / Keycloak | `shared/config/OAuth2SecurityConfig.java` | JWT auth, role-based access |
| Vault | `bootstrap.yml`, `infra/vault/` | Secrets, dynamic DB creds |
| Consul | `bootstrap.yml`, `infra/consul/` | Service discovery, hot config reload |
| GitHub Actions CI | `.github/workflows/ci.yml` | Build, test, scan, push image |
| GitHub Actions CD | `.github/workflows/cd.yml` | Deploy staging → prod, rollback |
| Kubernetes | `infra/k8s/` | Container orchestration, HPA, PDB |
| Terraform | `infra/terraform/` | EKS, RDS, Redis, DocumentDB, S3 |
| Micrometer | `shared/metrics/BankingMetrics.java` | Custom business metrics |
| OpenTelemetry | `infra/otel-collector-config.yml` | Distributed tracing → Jaeger |
| Structured Logging | `logback-spring.xml` | JSON logs → Loki |
| Gatling | `gatling/src/gatling/java/` | Load and performance testing |

---

## License

This project is built as a skills demonstration of production-grade banking backend engineering.

