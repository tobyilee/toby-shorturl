# TobyShortUrl

A full-featured URL shortener built with Spring Boot 3.5, featuring OAuth2 authentication, click analytics, QR code generation, and rate limiting.

## Features

- **URL Shortening** — Base62-encoded short codes with configurable length, expiration, and soft delete
- **OAuth2 Login** — Google and GitHub authentication with automatic user sync
- **Click Analytics** — Async event-driven tracking with privacy-first IP hashing (HMAC-SHA256)
  - Total / unique clicks, daily breakdown, top referrers, device & browser stats
  - Multi-version API (V1/V2) via `X-API-Version` header
- **QR Code Generation** — Dynamic PNG QR codes via ZXing with ETag caching
- **Rate Limiting** — Bucket4j token-bucket algorithm (100 req/min authenticated, 10 req/min anonymous)
- **Web Dashboard** — Thymeleaf-based UI for link management

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.5, Java 17 |
| Database | PostgreSQL + Flyway migrations |
| ORM | Spring Data JPA / Hibernate |
| Auth | Spring Security + OAuth2 Client |
| Rate Limit | Bucket4j + Caffeine cache |
| QR Code | Google ZXing 3.5.3 |
| UA Parsing | uap-java 1.6.1 |
| Template | Thymeleaf |
| Testing | JUnit 5, MockMvc, H2 |
| Coverage | JaCoCo |

## Getting Started

### Prerequisites

- Java 17+
- PostgreSQL

### Database Setup

```bash
createdb -U linkly linkly
```

Flyway migrations run automatically on startup.

### Environment Variables

```bash
export GITHUB_CLIENT_ID=your-github-client-id
export GITHUB_CLIENT_SECRET=your-github-client-secret
export IP_HASH_SECRET=your-secret-key
```

### Run

```bash
./gradlew bootRun
```

The app starts at `http://localhost:8080`.

## API Endpoints

### Authentication

| Method | Path | Description |
|---|---|---|
| GET | `/api/auth/me` | Current user info |

### Links

| Method | Path | Description |
|---|---|---|
| POST | `/api/links` | Create short link |
| GET | `/api/links` | List links (paginated) |
| GET | `/api/links/{id}` | Get link detail |
| PATCH | `/api/links/{id}` | Update link |
| DELETE | `/api/links/{id}` | Soft-delete link |
| GET | `/api/links/{id}/qr?size=200` | QR code (PNG) |

### Analytics

| Method | Path | Description |
|---|---|---|
| GET | `/api/links/{id}/stats?from=&to=` | Click statistics |

### Redirect

| Method | Path | Description |
|---|---|---|
| GET | `/{shortCode}` | 302 redirect to original URL |

## Project Structure

```
src/main/java/com/tobyshorturl/
├── config/          # Security, rate limiting, async config
├── identity/        # OAuth2 user management
├── link/            # URL shortening (API, service, domain, shortcode)
├── redirect/        # Short code → original URL redirect
├── analytics/       # Click tracking, enrichment, privacy
├── qr/              # QR code generation
└── web/view/        # Thymeleaf view controllers
```

## Testing

```bash
./gradlew test                  # Run tests with coverage
open build/reports/jacoco/test/html/index.html  # View coverage report
```
