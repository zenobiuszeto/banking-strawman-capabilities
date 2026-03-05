# Database Migrations Reference — Flyway

## 1. Dependencies

```groovy
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'
```

## 2. Configuration

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false   # only true for brownfield onboarding
    validate-on-migrate: true
    out-of-order: false          # enforce sequential migrations
    table: flyway_schema_history
```

## 3. Migration File Naming Convention

```
src/main/resources/db/migration/
├── V1__create_accounts_table.sql
├── V2__add_account_indexes.sql
├── V3__create_transactions_table.sql
├── V4__add_transactions_account_fk.sql
└── V5__add_audit_columns.sql
```

**Format**: `V{version}__{description}.sql`
- Version: integer or semver-style (V1, V1_1, V2)
- Description: snake_case, describes what changes
- Double underscore separator is required

## 4. Migration Best Practices

```sql
-- V1__create_accounts_table.sql
-- RULE: All migrations must be:
-- 1. Idempotent where possible (use IF NOT EXISTS)
-- 2. Backward compatible (don't drop columns until old code is fully retired)
-- 3. Fast for large tables (avoid full-table locks in prod)

CREATE TABLE IF NOT EXISTS accounts (
    id              VARCHAR(36)    PRIMARY KEY,
    customer_id     VARCHAR(36)    NOT NULL,
    account_type    VARCHAR(20)    NOT NULL CHECK (account_type IN ('CHECKING','SAVINGS','MONEY_MARKET')),
    balance         DECIMAL(19,4)  NOT NULL DEFAULT 0,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    version         BIGINT         NOT NULL DEFAULT 0
);

-- Always create indexes in a separate migration (non-blocking on large tables)
-- V2__add_account_indexes.sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_customer_id ON accounts(customer_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_status ON accounts(status);
```

## 5. Rename Column Safely (3-step process)

```sql
-- V10__add_email_address_column.sql  (step 1: add new column)
ALTER TABLE customers ADD COLUMN IF NOT EXISTS email_address VARCHAR(255);

-- Deploy code that writes to BOTH email AND email_address

-- V11__backfill_email_address.sql   (step 2: backfill in batches)
DO $$
DECLARE batch_size INT := 10000;
DECLARE last_id VARCHAR(36) := '';
BEGIN
  LOOP
    UPDATE customers SET email_address = email
    WHERE id > last_id AND email_address IS NULL
    ORDER BY id LIMIT batch_size
    RETURNING id INTO last_id;
    EXIT WHEN NOT FOUND;
    PERFORM pg_sleep(0.1);  -- throttle to reduce lock pressure
  END LOOP;
END $$;

-- V12__drop_email_column.sql        (step 3: drop after old code retired)
ALTER TABLE customers DROP COLUMN IF EXISTS email;
```

## 6. Test Migrations

```java
@SpringBootTest
@Testcontainers
class FlywayMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired Flyway flyway;

    @Test
    void allMigrationsSucceed() {
        MigrationInfoService info = flyway.info();
        assertThat(info.pending()).isEmpty();
        assertThat(info.failed()).isEmpty();
    }
}
```

## 7. Migrations Checklist

- [ ] Every migration file checked into version control
- [ ] Migrations tested in CI against a real PostgreSQL container
- [ ] No `DROP TABLE` or `DROP COLUMN` without a 3-step safe process
- [ ] `CREATE INDEX` uses `CONCURRENTLY` for tables > 100k rows
- [ ] Migration validates on startup (`validate-on-migrate: true`)
- [ ] `out-of-order: false` to enforce sequential history
