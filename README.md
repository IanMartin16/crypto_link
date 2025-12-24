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


---

## Authentication

All `/v1/**` endpoints (except ping/meta/symbols/fiats) require:

- Header: `x-api-key: <YOUR_API_KEY>`

### Admin endpoints require 2 headers:
- `x-admin-secret: <ADMIN_SECRET>`
- `x-master-admin: <MASTER_ADMIN_KEY>`

---

## Quick Smoke Tests (Prod)

### 1) Ping
```bash
curl -i https://cryptolink-production.up.railway.app/v1/ping

### 2) Health
curl -i https://cryptolink-production.up.railway.app/actuator/health

### 3) Docs blocked in prod (404 expected)
curl -i https://cryptolink-production.up.railway.app/v3/api-docs

## REST API
Get single price

GET /v1/price?symbol=BTC&fiat=USD
curl -s "https://cryptolink-production.up.railway.app/v1/price?symbol=BTC&fiat=USD" \
  -H "x-api-key: YOUR_API_KEY"

##
