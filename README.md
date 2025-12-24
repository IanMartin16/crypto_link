# CryptoLink

Real-time crypto price API (REST + SSE) with API keys, rate limiting, and production hardening.
Built with Spring Boot + PostgreSQL (Railway) + Flyway.

## Features

- REST price endpoints (`/v1/price`, `/v1/prices`)
- Server-Sent Events (SSE) stream for real-time updates (`/v1/stream/prices`)
- API keys stored in PostgreSQL
- Admin endpoints to create/revoke/modify keys (double-header protection)
- Rate limiting per plan (FREE/PRO/BUSINESS)
- Flyway migrations
- Production hardening:
  - Swagger/OpenAPI disabled in prod (404)
  - Minimal actuator exposure
  - Security headers
  - RequestId per request (`X-Request-Id`)

---

## Base URL (Production)

Example:
