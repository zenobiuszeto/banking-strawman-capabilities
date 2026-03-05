---
name: database
description: "Use this skill whenever the user wants to design, create, query, or manage a database for an application. Triggers include: 'schema design', 'database model', 'ORM', 'Prisma', 'SQLAlchemy', 'migrations', 'SQL query', 'PostgreSQL', 'MySQL', 'SQLite', 'MongoDB', 'indexing', 'relationships', 'foreign key', 'join', 'seed data', or any request involving persistent data storage. Also use for database optimization, connection pooling, or switching between databases. Do NOT use for in-memory caches like Redis (use the cache skill) or browser localStorage."
---

# Database Design & Management

## Overview

This skill covers relational databases (PostgreSQL, MySQL, SQLite) and document databases (MongoDB) for app development. It includes schema design, ORM usage (Prisma for Node.js, SQLAlchemy for Python), migrations, querying patterns, indexing, and seeding.

## Quick Reference

| Task | Approach |
|------|----------|
| Design schema | Use ERD principles — see Schema Design below |
| ORM (Node.js) | Prisma (recommended) or TypeORM |
| ORM (Python) | SQLAlchemy + Alembic for migrations |
| Raw queries | Avoid unless performance-critical; prefer ORM |
| Migrations | Always version-controlled — never edit the DB directly |
| Indexing | Index every foreign key and any column used in WHERE/ORDER BY |
| Seeding | Use deterministic seed scripts for reproducible dev data |

---

## Schema Design Principles

### CRITICAL: Plan before you code

1. **Identify entities** — nouns in your domain (User, Order, Product, etc.)
2. **Define relationships** — one-to-many, many-to-many, one-to-one
3. **Normalize to 3NF** — eliminate redundancy; each fact stored once
4. **Denormalize deliberately** — only for read performance, with clear justification

### Relationship Types

```
One-to-Many:   User ──< Orders          (one user has many orders)
Many-to-Many:  Products >──< Tags       (via join table: ProductTags)
One-to-One:    User ──── UserProfile    (profile belongs to exactly one user)
```

### Naming Conventions

| Object | Convention | Example |
|--------|-----------|---------|
| Tables | snake_case, plural | `user_profiles` |
| Columns | snake_case | `created_at`, `first_name` |
| Primary keys | `id` | `id SERIAL PRIMARY KEY` |
| Foreign keys | `{table}_id` | `user_id`, `order_id` |
| Join tables | Alphabetical | `product_tags` not `tag_products` |
| Timestamps | `created_at`, `updated_at` | Always include on every table |

---

## Prisma (Node.js ORM)

### Setup

```bash
npm install prisma @prisma/client
npx prisma init  # Creates prisma/schema.prisma and .env
```

**.env:**
```
DATABASE_URL="postgresql://user:password@localhost:5432/mydb"
```

### Schema (`prisma/schema.prisma`)

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

model User {
  id        Int      @id @default(autoincrement())
  email     String   @unique
  name      String
  role      Role     @default(USER)
  createdAt DateTime @default(now()) @map("created_at")
  updatedAt DateTime @updatedAt @map("updated_at")

  profile   Profile?
  orders    Order[]

  @@map("users")  // Table name in DB
}

enum Role {
  ADMIN
  USER
}

model Profile {
  id     Int    @id @default(autoincrement())
  bio    String?
  userId Int    @unique @map("user_id")
  user   User   @relation(fields: [userId], references: [id], onDelete: Cascade)

  @@map("profiles")
}

model Order {
  id        Int         @id @default(autoincrement())
  total     Decimal     @db.Decimal(10, 2)
  status    OrderStatus @default(PENDING)
  userId    Int         @map("user_id")
  user      User        @relation(fields: [userId], references: [id])
  items     OrderItem[]
  createdAt DateTime    @default(now()) @map("created_at")
  updatedAt DateTime    @updatedAt @map("updated_at")

  @@index([userId])  // Index every FK!
  @@map("orders")
}

enum OrderStatus {
  PENDING
  CONFIRMED
  SHIPPED
  DELIVERED
  CANCELLED
}

model Product {
  id    Int    @id @default(autoincrement())
  name  String
  price Decimal @db.Decimal(10, 2)
  tags  ProductTag[]

  @@map("products")
}

model Tag {
  id       Int          @id @default(autoincrement())
  name     String       @unique
  products ProductTag[]

  @@map("tags")
}

// Many-to-many join table
model ProductTag {
  productId Int     @map("product_id")
  tagId     Int     @map("tag_id")
  product   Product @relation(fields: [productId], references: [id])
  tag       Tag     @relation(fields: [tagId], references: [id])

  @@id([productId, tagId])
  @@map("product_tags")
}
```

### Migrations

```bash
# Create and apply a migration (development)
npx prisma migrate dev --name add_users_table

# Apply migrations in production (no prompts, no schema push)
npx prisma migrate deploy

# Reset DB (dev only — DESTROYS ALL DATA)
npx prisma migrate reset

# Inspect current DB state
npx prisma studio  # Opens browser-based DB viewer
```

### CRUD Queries (Prisma Client)

```typescript
import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

// CREATE
const user = await prisma.user.create({
  data: { email: 'alice@example.com', name: 'Alice' },
  select: { id: true, email: true, name: true }, // Return only needed fields
});

// READ ONE
const user = await prisma.user.findUnique({
  where: { id: 1 },
  include: { profile: true, orders: { take: 5, orderBy: { createdAt: 'desc' } } },
});

// READ MANY with pagination and filtering
const users = await prisma.user.findMany({
  where: {
    role: 'USER',
    email: { contains: '@example.com' },
    createdAt: { gte: new Date('2024-01-01') },
  },
  orderBy: { createdAt: 'desc' },
  skip: 0,
  take: 20,
});

// UPDATE
const updated = await prisma.user.update({
  where: { id: 1 },
  data: { name: 'Alice Updated' },
});

// UPSERT (create or update)
const user = await prisma.user.upsert({
  where: { email: 'alice@example.com' },
  update: { name: 'Alice' },
  create: { email: 'alice@example.com', name: 'Alice' },
});

// DELETE
await prisma.user.delete({ where: { id: 1 } });

// COUNT
const total = await prisma.user.count({ where: { role: 'ADMIN' } });

// TRANSACTION (atomic — all or nothing)
const [order, updatedInventory] = await prisma.$transaction([
  prisma.order.create({ data: { userId: 1, total: 99.99, status: 'PENDING' } }),
  prisma.product.update({ where: { id: 5 }, data: { stock: { decrement: 1 } } }),
]);

// RAW SQL (use sparingly)
const result = await prisma.$queryRaw`SELECT * FROM users WHERE email = ${email}`;
```

---

## SQLAlchemy (Python ORM)

### Setup

```bash
pip install sqlalchemy alembic psycopg2-binary python-dotenv --break-system-packages
alembic init alembic
```

### Models (`models.py`)

```python
from sqlalchemy import (
    Column, Integer, String, Numeric, DateTime, ForeignKey, Enum, UniqueConstraint, Index
)
from sqlalchemy.orm import relationship, DeclarativeBase
from sqlalchemy.sql import func
import enum

class Base(DeclarativeBase):
    pass

class Role(enum.Enum):
    ADMIN = "admin"
    USER = "user"

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, autoincrement=True)
    email = Column(String(255), unique=True, nullable=False, index=True)
    name = Column(String(100), nullable=False)
    role = Column(Enum(Role), default=Role.USER, nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    profile = relationship("Profile", back_populates="user", uselist=False, cascade="all, delete")
    orders = relationship("Order", back_populates="user")

class Order(Base):
    __tablename__ = "orders"

    id = Column(Integer, primary_key=True, autoincrement=True)
    total = Column(Numeric(10, 2), nullable=False)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    user = relationship("User", back_populates="orders")

    __table_args__ = (
        Index("ix_orders_user_id", "user_id"),  # Explicit index on FK
    )
```

### Database Connection (`database.py`)

```python
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
import os

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://user:pass@localhost/mydb")

engine = create_engine(DATABASE_URL, pool_size=10, max_overflow=20)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# FastAPI dependency
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
```

### Alembic Migrations

```bash
# Create a migration
alembic revision --autogenerate -m "add_users_table"

# Apply migrations
alembic upgrade head

# Rollback one step
alembic downgrade -1

# Show migration history
alembic history
```

---

## Indexing Strategy

### CRITICAL: Index these always

```sql
-- 1. Every foreign key
CREATE INDEX idx_orders_user_id ON orders(user_id);

-- 2. Columns used in WHERE clauses
CREATE INDEX idx_users_email ON users(email);

-- 3. Columns used in ORDER BY on large tables
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);

-- 4. Composite index for common multi-column filters (order matters!)
CREATE INDEX idx_orders_user_status ON orders(user_id, status);

-- 5. Unique constraint (also creates an index)
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
```

### Check if indexes are being used (PostgreSQL)

```sql
EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 1;
-- Look for "Index Scan" not "Seq Scan" on large tables
```

---

## Common SQL Patterns

```sql
-- Pagination (offset-based — simple, use for small datasets)
SELECT * FROM users ORDER BY created_at DESC LIMIT 20 OFFSET 40;

-- Pagination (cursor-based — use for large/real-time datasets)
SELECT * FROM orders WHERE created_at < '2024-01-15' ORDER BY created_at DESC LIMIT 20;

-- Aggregation
SELECT status, COUNT(*) as count, SUM(total) as revenue
FROM orders
GROUP BY status
HAVING COUNT(*) > 10;

-- JOIN patterns
SELECT u.name, COUNT(o.id) as order_count
FROM users u
LEFT JOIN orders o ON o.user_id = u.id  -- LEFT keeps users with 0 orders
GROUP BY u.id, u.name;

-- Upsert (PostgreSQL)
INSERT INTO users (email, name) VALUES ('alice@example.com', 'Alice')
ON CONFLICT (email) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Soft delete (never physically delete important data)
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;
UPDATE users SET deleted_at = NOW() WHERE id = 1;
SELECT * FROM users WHERE deleted_at IS NULL; -- Always filter in queries
```

---

## Seeding

### Prisma Seed (`prisma/seed.ts`)

```typescript
import { PrismaClient } from '@prisma/client';
const prisma = new PrismaClient();

async function main() {
  // Use upsert for idempotent seeds (safe to run multiple times)
  const alice = await prisma.user.upsert({
    where: { email: 'alice@example.com' },
    update: {},
    create: {
      email: 'alice@example.com',
      name: 'Alice',
      role: 'ADMIN',
    },
  });

  console.log({ alice });
}

main()
  .catch(console.error)
  .finally(() => prisma.$disconnect());
```

Add to `package.json`:
```json
"prisma": {
  "seed": "ts-node prisma/seed.ts"
}
```

```bash
npx prisma db seed
```

---

## Connection Pooling

```typescript
// Prisma — configure in schema.prisma datasource
datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
  // Use PgBouncer URL for serverless environments:
  // postgresql://user:pass@host:6432/db?pgbouncer=true&connection_limit=1
}

// Or use environment variable:
// DATABASE_URL="postgresql://...?connection_limit=10&pool_timeout=30"
```

---

## Critical Rules

- **Never edit the database directly in production** — always use migrations
- **Always index foreign keys** — unindexed FKs cause full table scans on joins
- **Always include `created_at` and `updated_at`** on every table
- **Never use `SELECT *` in production code** — specify columns explicitly
- **Use transactions for multi-step writes** — maintain data integrity
- **Never store passwords in plain text** — use bcrypt/argon2 (see auth skill)
- **Never put secrets in migration files** — use environment variables
- **Use soft deletes for business data** — add `deleted_at` instead of hard delete
- **Test migrations on a copy of production data** before running in prod
- **Use connection pooling** — never open a new DB connection per request
- **Validate data at the application layer** — don't rely solely on DB constraints
