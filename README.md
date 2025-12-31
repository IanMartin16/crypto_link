# CryptoLink (crypto_link)

API REST + SSE para cotizaciones de criptomonedas en tiempo real-ish (polling + broadcast), con:
- API Keys + planes (FREE/BUSINESS)
- Rate limit con headers “pro”
- Catálogo de symbols/fiats desde PostgreSQL
- SSE con token (para evitar headers en EventSource/curl)
- Hardening para /admin con doble header

## Stack
- Java 17
- Spring Boot (WebMVC, Actuator)
- PostgreSQL (Railway)
- Flyway (migraciones)
- Prometheus metrics (opcional)

---

## Endpoints principales

### Públicos (sin API key)
- `GET /v1/ping`
- `GET /v1/meta`
- `GET /v1/symbols`
- `GET /v1/fiats`

### Requieren `x-api-key`
- `GET /v1/me` (info del plan/límites del API key actual)
- `GET /v1/price?symbol=BTC&fiat=USD`
- `GET /v1/prices?symbols=BTC,ETH,SOL&fiat=EUR`
- `GET /v1/auth/sse-token` (genera token para SSE)

### SSE (requiere token o api-key; recomendado token)
- `GET /v1/stream/prices?token=...&symbols=BTC,ETH&fiat=USD`

### Admin (doble candado)
- `POST /admin/v1/keys` (crear key)
- `POST /admin/v1/keys/{apiKey}/revoke`
- `POST /admin/v1/keys/{apiKey}/plan`
- `POST /admin/v1/keys/{apiKey}/expires`

Headers requeridos:
- `x-admin-secret`
- `x-master-admin`

---

## Rate limit (headers)
En respuestas protegidas vas a ver headers tipo:
- `X-Plan: FREE|BUSINESS`
- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `X-RateLimit-Reset` (epoch seconds)
- `X-RateLimit-Used`
- `Retry-After` (solo cuando pega 429)

---

## Setup local

### Requisitos
- Java 17
- Maven
- PostgreSQL local (o cualquier Postgres)

### Config (recomendado)
Crea un archivo `local.properties` (NO se sube al repo) y agrégalo a `.gitignore`.

Ejemplo `local.properties`:

```properties
# DB (local)
spring.datasource.url=jdbc:postgresql://localhost:5432/cryptolink
spring.datasource.username=postgres
spring.datasource.password=postgres

# Admin (solo para ti)
cryptolink.admin.secret=CHANGE_ME_ADMIN
cryptolink.master.admin.key=CHANGE_ME_MASTER

# CoinGecko
cryptolink.coingecko.base-url=https://api.coingecko.com/api/v3
cryptolink.coingecko.timeout-ms=5000

# SSE
cryptolink.sse.keepalive-ms=25000

## Billing (Stripe)

CryptoLink usa **Stripe** para suscripciones mensuales y emite/actualiza el **API Key** automáticamente al confirmar el pago.

### Planes
- **FREE**: 60 req/min, 1 SSE connection, maxSymbols=3
- **BUSINESS**: 600 req/min, 5 SSE connections, maxSymbols=20
- **PRO**: 1200 req/min, 10 SSE connections, maxSymbols=25

> Puedes ver tu plan y límites actuales en:
`GET /v1/me` (requiere `x-api-key`)

---

## Opción A: Comprar con Stripe Payment Links (rápido)

- PRO: <https://buy.stripe.com/test_aFabJ1cd251ofD87AR3F601>
- BUSINESS: <https://buy.stripe.com/test_3cI7sLcd2ctQ0Ie5sJ3F600>

Después del pago, recibirás tu **API Key** por correo (y también podrás verla con `/v1/me` si ya la tienes).

---

## Opción B: Checkout desde la API (recomendado para integración)

### 1) Crear sesión de Checkout
(El backend crea la sesión y te regresa un `url` de Stripe Checkout)

**Ejemplo: BUSINESS**
```bash
curl -s -X POST "https://cryptolink-production.up.railway.app/v1/billing/checkout?plan=BUSINESS" \
  -H "Content-Type: application/json" \
  -d '{"email":"tu_correo@dominio.com"}'

# CryptoLink (by evi_link.dev)

CryptoLink is a simple, production-ready crypto pricing API powered by CoinGecko.
It supports **multiple symbols**, **multiple fiats (USD/MXN/EUR)**, **SSE streaming**, **rate limits**, and **plans** (BUSINESS / PRO).

**Base URL (PROD):**  
https://cryptolink-production.up.railway.app

---

## Features

- ✅ `/v1/prices` multi-quote endpoint
- ✅ `/v1/stream/prices` real-time streaming via **SSE**
- ✅ API keys + plans (FREE / BUSINESS / PRO)
- ✅ Rate limit headers (`X-RateLimit-*`)
- ✅ Max symbols per plan (enforced)
- ✅ Supported symbols/fiats from DB catalog
- ✅ Health endpoints via actuator

---

## Quick Start

### 1) Ping
```bash
curl -i https://cryptolink-production.up.railway.app/v1/ping
