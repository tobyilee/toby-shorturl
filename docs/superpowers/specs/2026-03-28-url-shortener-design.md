# Linkly — URL Shortener + Analytics Service

## Overview

Linkly는 Spring Boot 4 (Spring Framework 7) 기반의 SaaS URL 단축 + 분석 서비스이다. 일반 대중이 가입하여 사용할 수 있는 공개 서비스로, URL 단축, 클릭 분석, QR 코드 생성을 핵심 기능으로 제공한다.

## Architecture

**접근법: 모놀리식 + 비동기 분석**

하나의 Spring Boot 애플리케이션에 모든 기능을 포함하되, 클릭 분석은 `ApplicationEventPublisher` + `@Async` 리스너로 비동기 처리하여 리다이렉트 성능을 보장한다.

```
[Spring Boot 4 Application]
├── Redirect Controller (GET /{code} → 302 + 이벤트 발행)
├── REST API (v1/v2, @GetMapping(version=...))
├── Web UI (Thymeleaf) — /app/** 경로 아래
├── Service Layer (LinkService, AnalyticsService, QrCodeService)
├── 비동기 분석 (@Async + @EventListener)
└── Repository Layer (Spring Data JPA → PostgreSQL)
```

### 경로 충돌 방지

리다이렉트가 루트 `/{code}`에 있으므로 Thymeleaf UI와 정적 리소스 충돌을 방지하기 위해:
- Web UI: `/app/**` 경로 아래 고정 (예: `/app/dashboard`, `/app/links`)
- 인증: `/oauth2/**`, `/api/auth/**`
- 정적 리소스: `/static/**`
- 리다이렉트 컨트롤러는 `/api/**`, `/app/**`, `/oauth2/**`, `/static/**` 패턴 제외

### 비동기 이벤트 신뢰성

`ApplicationEventPublisher` + `@Async`는 프로세스 내부 비동기이므로, JVM 종료 시 미처리 이벤트가 유실될 수 있다.

**MVP 정책:** Analytics는 eventual consistency이며 유실 가능성을 허용한다. `@Async` executor에 적절한 설정을 둔다:
- Core pool size: 2
- Max pool size: 10
- Queue capacity: 1000
- Rejection policy: CallerRunsPolicy (큐 초과 시 호출 스레드에서 실행)

**향후 개선:** Spring Modulith event publication repository 또는 outbox 패턴으로 전환하여 durable event 처리를 보장한다.

### Spring Boot 4 신기능 활용

| 기능 | 활용 위치 | 설명 |
|------|----------|------|
| `@GetMapping(version = "...")` | 분석 API v1/v2 버전 관리 | `WebMvcConfigurer#configureApiVersioning` + resolver 설정 |
| `@Retryable` / `@EnableResilientMethods` | OAuth2 provider 호출 실패 재시도 | 외부 서비스 호출 시에만 적용 |
| `RestClient` | 향후 외부 API 연동 (GeoIP, webhook 등) | RestTemplate 대체 |
| `RestTestClient` | 통합 테스트 (`@AutoConfigureRestTestClient` 명시 필요) | |
| `@MockitoBean` | Spring ApplicationContext 내 bean override 테스트 | 순수 단위 테스트는 Mockito `@Mock` 사용 |
| `spring-boot-starter-webmvc` | 웹 스타터 (기존 starter-web 대체) | |
| Jackson 3.x | JSON 직렬화 (`tools.jackson` 패키지) | |
| `ProblemDetail` | RFC 9457 표준 에러 응답 | 커스텀 에러 포맷 대신 프레임워크 기본 사용 |

### Rate Limiting

`@ConcurrencyLimit`는 동시 실행 제한(concurrency throttle)이지 Rate Limiting이 아니다. 실제 API Rate Limiting은 Bucket4j + Redis를 사용한다:
- 비인증 사용자: IP당 분당 10회
- 인증 사용자: 사용자당 분당 100회
- Rate Limit 초과 시 `429 Too Many Requests` + `Retry-After` 헤더

`@ConcurrencyLimit`는 내부 고비용 메서드 보호 용도로만 한정한다 (예: QR 코드 생성, 분석 집계 쿼리).

## Data Model

### users

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 자동 생성 |
| email | VARCHAR | 이메일 (verified email인 경우만 저장) |
| name | VARCHAR | 사용자 이름 |
| provider | VARCHAR | OAuth2 제공자 (google, github) |
| provider_id | VARCHAR | 제공자 고유 ID |
| created_at | TIMESTAMP | 생성 시각 |
| updated_at | TIMESTAMP | 수정 시각 |

**제약 조건:**
- `UNIQUE(provider, provider_id)` — 동일 provider 중복 가입 방지
- `email`은 UNIQUE가 아님 — 여러 provider로 같은 이메일 가입 가능 (추후 계정 연동 정책 결정 시 변경)

### links

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 자동 생성 |
| short_code | VARCHAR UNIQUE | 단축 코드 (Base62, 6~8자) |
| original_url | TEXT | 원본 URL |
| title | VARCHAR NULL | 사용자 지정 제목 |
| user_id | BIGINT FK | 소유자 |
| click_count | BIGINT DEFAULT 0 | 캐시된 클릭 수 (eventual consistency, 원천은 click_events) |
| active | BOOLEAN DEFAULT TRUE | 일시 비활성화 용도 |
| deleted_at | TIMESTAMP NULL | soft delete 시각 (NULL이면 삭제되지 않음) |
| expires_at | TIMESTAMP NULL | 만료 시간 (선택) |
| created_at | TIMESTAMP | 생성 시각 |
| updated_at | TIMESTAMP | 수정 시각 |

### click_events

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 자동 생성 |
| link_id | BIGINT FK | 링크 참조 |
| clicked_at | TIMESTAMP | 클릭 시각 |
| ip_hash | VARCHAR | IP HMAC 해시 (application secret + IP → HMAC-SHA256) |
| user_agent | TEXT | User-Agent 원본 (길이 편차가 크므로 TEXT) |
| referer | TEXT NULL | Referer 헤더 |
| device_type | VARCHAR NULL | mobile/desktop/tablet |
| browser | VARCHAR NULL | 브라우저 이름 |

### Indexes

- `links(short_code)` — UNIQUE, 리다이렉트 조회
- `links(user_id, deleted_at)` — 사용자별 활성 링크 목록
- `click_events(link_id, clicked_at)` — 링크별 시간대 분석 (복합 인덱스)

### Unique Click 정의

고유 클릭(unique click)은 **동일 ip_hash + user_agent + 날짜** 기준으로 중복을 제거한다. NAT 환경에서 과소계상 가능성이 있으나, 개인정보 최소 수집 원칙과 MVP 단계를 고려한 합리적 기준이다.

### Short Code 생성

Base62 인코딩 (a-z, A-Z, 0-9). 6자 기본 (62^6 = 약 568억 조합). `SecureRandom`으로 생성하고, 충돌 시 재생성한다. 점유율이 높아지면 adaptive length 또는 pre-allocation 전략으로 전환한다.

### 데이터 보존 정책

- Raw click_events: 90일 보관
- 일별 rollup 통계: 영구 보관
- 보존 정책은 스케줄러로 자동 정리

## API Design

### Redirect

```
GET /{shortCode} → 302 Redirect (인증 불필요)
```

리다이렉트 후 `ApplicationEventPublisher`로 `ClickEvent`를 비동기 발행한다.

**상태별 응답:**
- 존재하지 않는 코드: `404 Not Found`
- soft delete된 링크: `410 Gone`
- 만료된 링크: `410 Gone`
- 비활성(active=false) 링크: `404 Not Found`

### Link Management

| Method | Path | 설명 |
|--------|------|------|
| POST | /api/links | 링크 생성 |
| GET | /api/links | 내 링크 목록 (페이징, deleted_at IS NULL) |
| GET | /api/links/{id} | 링크 상세 |
| PATCH | /api/links/{id} | 링크 수정 (title, active) |
| DELETE | /api/links/{id} | 링크 삭제 (deleted_at 설정) |

### Analytics

Spring Framework 7의 API versioning을 사용한다. `WebMvcConfigurer#configureApiVersioning`에서 header 기반 resolver를 설정하고, `@GetMapping(version = "...")`으로 버전별 매핑한다.

**v1 — 기본 통계 (`@GetMapping(version = "1")`):**

```json
GET /api/links/{id}/stats?from=2026-03-01&to=2026-03-28
{
  "totalClicks": 1234,
  "uniqueClicks": 891,
  "clicksByDate": [
    {"date": "2026-03-28", "clicks": 45}
  ]
}
```

**v2 — 상세 분석 (`@GetMapping(version = "2")`):**

```json
GET /api/links/{id}/stats?from=2026-03-01&to=2026-03-28&limit=20
{
  "totalClicks": 1234,
  "uniqueClicks": 891,
  "clicksByDate": [...],
  "topReferers": ["twitter.com", "google.com"],
  "deviceBreakdown": {"mobile": 60, "desktop": 35, "tablet": 5},
  "browserBreakdown": {"Chrome": 55, "Safari": 30},
  "recentClicks": [...]
}
```

**Analytics 쿼리 파라미터:**
- `from`, `to` — 날짜 범위 (필수, 최대 90일)
- `limit` — recentClicks 제한 (기본 10, 최대 100)

### QR Code

```
GET /api/links/{id}/qr          → PNG 이미지 (기본 200px)
GET /api/links/{id}/qr?size=300 → 크기 지정
```

QR 코드는 deterministic resource이므로 `Cache-Control: public, max-age=86400` + `ETag` 헤더를 설정한다.

### Authentication

**인증 모델 분리:**
- **Web UI (Thymeleaf):** Spring Security session/cookie 기반. 표준 OAuth2 Login 사용.
- **외부 API:** 향후 API Key 또는 JWT bearer token 방식으로 별도 제공 (현재 MVP 스코프 외).

| Method | Path | 설명 |
|--------|------|------|
| GET | /oauth2/authorization/{provider} | 소셜 로그인 시작 (Spring Security 기본 경로) |
| GET | /login/oauth2/code/{provider} | 콜백 처리 (Spring Security 기본 경로) |
| POST | /app/logout | 로그아웃 (session 무효화) |
| GET | /api/auth/me | 현재 사용자 정보 |

OAuth2 콜백 경로는 Spring Security의 기본 경로(`/login/oauth2/code/{provider}`)를 그대로 사용하여 구현 리스크를 낮춘다.

## Error Handling

Spring Framework 7의 `ProblemDetail` (RFC 9457)을 사용하여 표준 에러 응답을 반환한다.

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Link not found: abc123",
  "instance": "/abc123"
}
```

글로벌 `@RestControllerAdvice`에서 커스텀 예외를 `ErrorResponseException`으로 매핑한다.

주요 에러 코드:
- `404 Not Found` — 존재하지 않는 / 비활성 링크
- `410 Gone` — 만료 또는 삭제된 링크
- `429 Too Many Requests` — Rate Limit 초과 (Bucket4j)
- `401 Unauthorized` — 인증 필요
- `400 Bad Request` — 잘못된 요청 (유효하지 않은 URL 등)

## Package Structure

```
com.linkly
├── config/              # SecurityConfig, AsyncConfig, WebMvcConfig, RateLimitConfig
├── link/
│   ├── domain/          # Link entity
│   ├── repository/      # LinkRepository
│   ├── service/         # LinkService
│   ├── shortcode/       # Base62Encoder, ShortCodeGenerator
│   └── api/             # LinkApiController
├── analytics/
│   ├── domain/          # ClickEvent entity
│   ├── repository/      # ClickEventRepository
│   ├── service/         # AnalyticsService
│   ├── event/           # ClickEventPayload, ClickEventListener
│   ├── enrichment/      # UserAgentParser
│   ├── privacy/         # IpHasher (HMAC)
│   └── api/             # AnalyticsApiController
├── identity/
│   ├── domain/          # User entity
│   ├── repository/      # UserRepository
│   ├── service/         # OAuth2UserService
│   └── api/             # AuthApiController
├── qr/
│   └── service/         # QrCodeService
├── redirect/
│   └── web/             # RedirectController
├── web/
│   └── view/            # DashboardViewController, LoginViewController
└── LinklyApplication.java
```

각 도메인 모듈(link, analytics, identity, redirect)은 독립적인 concern을 가지며, 의존 방향은 다음과 같다:
- `redirect` → `link` (short_code 조회)
- `redirect` → `analytics` (이벤트 발행)
- `analytics`는 `link`에 의존하지 않음 (link_id만 참조)
- `identity`는 독립적

## Web UI (Thymeleaf)

- `/app/login` — 로그인 페이지 (Google/GitHub 소셜 로그인)
- `/app/dashboard` — 대시보드 (내 링크 목록, 기본 통계)
- `/app/links/new` — 링크 생성 폼
- `/app/links/{id}` — 링크 상세 (클릭 통계 차트, QR 코드)
- `/app/links/{id}/edit` — 링크 수정 폼

## Tech Stack

- Java 17+
- Spring Boot 4.x (Spring Framework 7)
- Gradle (Kotlin DSL)
- PostgreSQL
- Spring Data JPA (Hibernate 7)
- Spring Security + OAuth2 Client (session/cookie 기반)
- Thymeleaf
- Flyway — DB 마이그레이션
- Bucket4j + Caffeine — Rate Limiting (Redis 없이 로컬 캐시로 시작)
- QR 코드: ZXing 라이브러리
- User-Agent 파싱: ua-parser 라이브러리
- Jackson 3.x

## Future Considerations (현재 스코프 외)

- 커스텀 도메인 지원 (구조만 준비)
- GeoIP 기반 국가/도시 분석
- UTM 파라미터 추적
- A/B 테스트 (같은 단축 URL로 다른 목적지)
- Spring Modulith로 모듈 경계 선언 및 durable event publication 전환
- OpenTelemetry starter로 관측 가능성 확보
- HTTP Service Clients (`@ImportHttpServices`)로 외부 API 연동
- 외부 API용 JWT/API Key 인증
- 대시보드 실시간 업데이트 (WebSocket/SSE)
