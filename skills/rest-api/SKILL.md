---
name: rest-api
description: "Use this skill whenever the user wants to design, scaffold, implement, or debug a REST API. Triggers include: 'build an API', 'create an endpoint', 'REST', 'CRUD routes', 'Express', 'FastAPI', 'Node backend', 'API routes', 'HTTP methods', 'request/response', 'middleware', 'API versioning', or any request to expose data over HTTP. Also use when the user wants to document an API (OpenAPI/Swagger), test endpoints, or handle API errors. Do NOT use for GraphQL APIs or gRPC."
---

# REST API Development

## Overview

A REST API exposes resources over HTTP using standard verbs (GET, POST, PUT, PATCH, DELETE). This skill covers scaffolding, routing, middleware, validation, error handling, and documentation for Node.js (Express) and Python (FastAPI) backends.

## Quick Reference

| Task | Approach |
|------|----------|
| Scaffold a new API | Use Express (Node) or FastAPI (Python) — see Scaffolding below |
| Define CRUD routes | Follow resource-based routing patterns |
| Validate request data | Use Zod (Node) or Pydantic (Python) |
| Handle errors consistently | Use centralized error middleware |
| Document the API | Use OpenAPI/Swagger annotations |
| Test endpoints | Use curl, HTTPie, or Jest/pytest |

---

## Scaffolding

### Node.js / Express

```bash
mkdir my-api && cd my-api
npm init -y
npm install express zod helmet cors morgan dotenv
npm install -D typescript ts-node @types/express @types/node nodemon
npx tsc --init
```

**Entry point (`src/index.ts`):**
```typescript
import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import morgan from 'morgan';
import { router } from './routes';
import { errorHandler } from './middleware/errorHandler';

const app = express();

app.use(helmet());
app.use(cors());
app.use(morgan('dev'));
app.use(express.json());

app.use('/api/v1', router);
app.use(errorHandler); // Must be last

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Server running on port ${PORT}`));
```

### Python / FastAPI

```bash
pip install fastapi uvicorn pydantic python-dotenv --break-system-packages
```

**Entry point (`main.py`):**
```python
from fastapi import FastAPI
from contextlib import asynccontextmanager
from routers import users, items

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup logic (DB connections, etc.)
    yield
    # Shutdown logic

app = FastAPI(title="My API", version="1.0.0", lifespan=lifespan)

app.include_router(users.router, prefix="/api/v1/users", tags=["users"])
app.include_router(items.router, prefix="/api/v1/items", tags=["items"])
```

```bash
uvicorn main:app --reload
```

---

## Resource-Based Routing

Always design routes around **nouns** (resources), not verbs.

| Method | Path | Action |
|--------|------|--------|
| GET | `/users` | List all users |
| GET | `/users/:id` | Get one user |
| POST | `/users` | Create a user |
| PUT | `/users/:id` | Replace a user |
| PATCH | `/users/:id` | Partially update a user |
| DELETE | `/users/:id` | Delete a user |
| GET | `/users/:id/orders` | Nested resource |

### Express Routes (`src/routes/users.ts`)

```typescript
import { Router } from 'express';
import { z } from 'zod';
import { validate } from '../middleware/validate';
import * as usersController from '../controllers/users';

const router = Router();

const CreateUserSchema = z.object({
  name: z.string().min(1).max(100),
  email: z.string().email(),
  role: z.enum(['admin', 'user']).default('user'),
});

router.get('/', usersController.list);
router.get('/:id', usersController.getOne);
router.post('/', validate(CreateUserSchema), usersController.create);
router.patch('/:id', usersController.update);
router.delete('/:id', usersController.remove);

export { router as usersRouter };
```

### FastAPI Routes (`routers/users.py`)

```python
from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel, EmailStr
from typing import Optional

router = APIRouter()

class UserCreate(BaseModel):
    name: str
    email: EmailStr
    role: str = "user"

class UserResponse(BaseModel):
    id: int
    name: str
    email: str
    role: str

    class Config:
        from_attributes = True  # Enable ORM mode

@router.get("/", response_model=list[UserResponse])
async def list_users(skip: int = 0, limit: int = 20):
    return await get_users_from_db(skip=skip, limit=limit)

@router.get("/{user_id}", response_model=UserResponse)
async def get_user(user_id: int):
    user = await get_user_by_id(user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user

@router.post("/", response_model=UserResponse, status_code=status.HTTP_201_CREATED)
async def create_user(body: UserCreate):
    return await create_user_in_db(body)
```

---

## Validation Middleware

### Express + Zod (`src/middleware/validate.ts`)

```typescript
import { Request, Response, NextFunction } from 'express';
import { ZodSchema } from 'zod';

export const validate = (schema: ZodSchema) =>
  (req: Request, res: Response, next: NextFunction) => {
    const result = schema.safeParse(req.body);
    if (!result.success) {
      return res.status(400).json({
        error: 'Validation failed',
        details: result.error.flatten().fieldErrors,
      });
    }
    req.body = result.data;
    next();
  };
```

---

## Error Handling

### CRITICAL: Always use a centralized error handler

**Express (`src/middleware/errorHandler.ts`):**
```typescript
import { Request, Response, NextFunction } from 'express';

export class AppError extends Error {
  constructor(
    public statusCode: number,
    public message: string,
    public isOperational = true
  ) {
    super(message);
  }
}

export const errorHandler = (
  err: Error,
  req: Request,
  res: Response,
  next: NextFunction
) => {
  if (err instanceof AppError) {
    return res.status(err.statusCode).json({
      status: 'error',
      message: err.message,
    });
  }

  // Unexpected errors - don't leak internals
  console.error('Unhandled error:', err);
  res.status(500).json({
    status: 'error',
    message: 'Internal server error',
  });
};
```

**FastAPI:**
```python
from fastapi import Request
from fastapi.responses import JSONResponse

class AppError(Exception):
    def __init__(self, status_code: int, message: str):
        self.status_code = status_code
        self.message = message

@app.exception_handler(AppError)
async def app_error_handler(request: Request, exc: AppError):
    return JSONResponse(
        status_code=exc.status_code,
        content={"status": "error", "message": exc.message}
    )
```

### Standard Error Response Shape

Always return a consistent JSON shape for errors:
```json
{
  "status": "error",
  "message": "User not found",
  "details": { "field": ["error message"] }  // Optional, for validation errors
}
```

---

## API Versioning

**CRITICAL: Always version your API from day one.**

```typescript
// URL versioning (recommended for public APIs)
app.use('/api/v1', v1Router);
app.use('/api/v2', v2Router);

// Header versioning (internal APIs)
app.use((req, res, next) => {
  const version = req.headers['api-version'] || 'v1';
  req.apiVersion = version;
  next();
});
```

---

## Pagination, Filtering, and Sorting

```typescript
// Standard query params: ?page=1&limit=20&sort=name&order=asc&filter[role]=admin
router.get('/', async (req, res) => {
  const { page = 1, limit = 20, sort = 'createdAt', order = 'desc' } = req.query;

  const skip = (Number(page) - 1) * Number(limit);
  const [data, total] = await Promise.all([
    db.user.findMany({ skip, take: Number(limit), orderBy: { [sort as string]: order } }),
    db.user.count(),
  ]);

  res.json({
    data,
    meta: {
      total,
      page: Number(page),
      limit: Number(limit),
      totalPages: Math.ceil(total / Number(limit)),
    },
  });
});
```

---

## HTTP Status Codes — Quick Reference

| Scenario | Code |
|----------|------|
| Success (read/update) | 200 OK |
| Created | 201 Created |
| No content (delete) | 204 No Content |
| Bad request / validation | 400 Bad Request |
| Unauthorized (not logged in) | 401 Unauthorized |
| Forbidden (no permission) | 403 Forbidden |
| Not found | 404 Not Found |
| Conflict (duplicate) | 409 Conflict |
| Unprocessable entity | 422 Unprocessable Entity |
| Rate limited | 429 Too Many Requests |
| Server error | 500 Internal Server Error |

---

## Middleware Stack (Express)

```typescript
// Recommended middleware order:
app.use(helmet());           // 1. Security headers
app.use(cors(corsOptions));  // 2. CORS
app.use(morgan('dev'));       // 3. Logging
app.use(express.json({ limit: '10mb' })); // 4. Body parsing
app.use(rateLimit({ windowMs: 15 * 60 * 1000, max: 100 })); // 5. Rate limiting
app.use('/api/v1', authenticate); // 6. Auth (route-specific)
app.use('/api/v1', router);  // 7. Routes
app.use(errorHandler);       // 8. Error handler (MUST be last)
```

---

## OpenAPI / Swagger Documentation

### Express (swagger-jsdoc + swagger-ui-express)

```bash
npm install swagger-jsdoc swagger-ui-express
```

```typescript
/**
 * @swagger
 * /api/v1/users:
 *   get:
 *     summary: List all users
 *     tags: [Users]
 *     parameters:
 *       - in: query
 *         name: page
 *         schema:
 *           type: integer
 *         description: Page number
 *     responses:
 *       200:
 *         description: A list of users
 */
```

### FastAPI (built-in)
FastAPI auto-generates OpenAPI docs. Access at:
- `/docs` — Swagger UI
- `/redoc` — ReDoc UI
- `/openapi.json` — Raw schema

---

## Testing Endpoints

```bash
# Create user
curl -X POST http://localhost:3000/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice", "email": "alice@example.com"}'

# List users with pagination
curl "http://localhost:3000/api/v1/users?page=1&limit=10"

# Update
curl -X PATCH http://localhost:3000/api/v1/users/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice Updated"}'

# Delete
curl -X DELETE http://localhost:3000/api/v1/users/1
```

---

## Critical Rules

- **Always version your API** — `/api/v1/` from day one; never break existing clients
- **Use nouns, not verbs** — `/users` not `/getUsers`
- **Return consistent error shapes** — always `{ status, message, details? }`
- **Validate all input** — never trust req.body without Zod/Pydantic validation
- **Set CORS explicitly** — never use `cors()` with no options in production
- **Use 201 for POST creates**, 204 for DELETEs with no body
- **Never expose stack traces** in production error responses
- **Rate-limit all public endpoints** — protect against abuse
- **Log all errors** with context (request ID, user ID, route)
- **Use environment variables** — never hard-code secrets or connection strings
