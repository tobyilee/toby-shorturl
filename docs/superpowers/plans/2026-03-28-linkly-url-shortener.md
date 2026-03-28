# Linkly URL Shortener Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Boot 4 기반 SaaS URL 단축 + 분석 서비스를 구축한다.

**Architecture:** 모놀리식 + 비동기 분석. 리다이렉트는 302 즉시 응답 후 `ApplicationEventPublisher`로 클릭 이벤트를 비동기 발행한다. Spring Framework 7의 API versioning, ProblemDetail, Bucket4j rate limiting을 활용한다.

**Tech Stack:** Java 17+, Spring Boot 4.x, Gradle (Kotlin DSL), PostgreSQL, Spring Data JPA, Spring Security OAuth2, Thymeleaf, Flyway, Bucket4j, ZXing, ua-parser, Jackson 3.x

**Design Spec:** `docs/superpowers/specs/2026-03-28-url-shortener-design.md`

---

## File Structure

```
linkly/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/java/com/linkly/
│   ├── LinklyApplication.java
│   ├── config/
│   │   ├── AsyncConfig.java
│   │   ├── SecurityConfig.java
│   │   ├── WebMvcConfig.java
│   │   └── RateLimitConfig.java
│   ├── link/
│   │   ├── domain/Link.java
│   │   ├── repository/LinkRepository.java
│   │   ├── service/LinkService.java
│   │   ├── shortcode/Base62Encoder.java
│   │   ├── shortcode/ShortCodeGenerator.java
│   │   └── api/LinkApiController.java
│   ├── analytics/
│   │   ├── domain/ClickEvent.java
│   │   ├── repository/ClickEventRepository.java
│   │   ├── service/AnalyticsService.java
│   │   ├── event/ClickEventPayload.java
│   │   ├── event/ClickEventListener.java
│   │   ├── enrichment/UserAgentParser.java
│   │   ├── privacy/IpHasher.java
│   │   └── api/AnalyticsApiController.java
│   ├── identity/
│   │   ├── domain/User.java
│   │   ├── repository/UserRepository.java
│   │   ├── service/CustomOAuth2UserService.java
│   │   └── api/AuthApiController.java
│   ├── qr/
│   │   └── service/QrCodeService.java
│   ├── redirect/
│   │   └── web/RedirectController.java
│   └── web/
│       └── view/
│           ├── DashboardViewController.java
│           └── LoginViewController.java
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/
│   │   └── V1__init_schema.sql
│   └── templates/
│       ├── login.html
│       ├── dashboard.html
│       ├── link-create.html
│       ├── link-detail.html
│       └── link-edit.html
└── src/test/java/com/linkly/
    ├── link/
    │   ├── shortcode/Base62EncoderTest.java
    │   ├── shortcode/ShortCodeGeneratorTest.java
    │   ├── service/LinkServiceTest.java
    │   └── api/LinkApiControllerTest.java
    ├── analytics/
    │   ├── privacy/IpHasherTest.java
    │   ├── enrichment/UserAgentParserTest.java
    │   ├── service/AnalyticsServiceTest.java
    │   └── api/AnalyticsApiControllerTest.java
    ├── redirect/
    │   └── web/RedirectControllerTest.java
    ├── qr/
    │   └── service/QrCodeServiceTest.java
    └── identity/
        └── service/CustomOAuth2UserServiceTest.java
```

---

## Task 1: 프로젝트 초기화 + Gradle 설정

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `src/main/java/com/linkly/LinklyApplication.java`
- Create: `src/main/resources/application.yml`

- [ ] **Step 1: Spring Initializr로 프로젝트 생성**

```bash
curl -s "https://start.spring.io/starter.zip?type=gradle-project-kotlin&language=java&bootVersion=4.0.0&groupId=com.linkly&artifactId=linkly&name=linkly&packageName=com.linkly&javaVersion=17&dependencies=webmvc,data-jpa,security,oauth2-client,thymeleaf,flyway,postgresql,validation" -o /tmp/linkly.zip && unzip -o /tmp/linkly.zip -d . && rm /tmp/linkly.zip
```

만약 Spring Initializr에서 Boot 4가 아직 지원되지 않으면, Boot 3.5.x로 생성 후 `build.gradle.kts`를 수동으로 Boot 4로 수정한다.

- [ ] **Step 2: build.gradle.kts에 추가 의존성 설정**

`build.gradle.kts`에 다음 의존성을 추가/확인한다:

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.linkly"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot 4 starters
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // QR Code
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    // User-Agent parsing
    implementation("com.github.ua-parser:uap-java:1.6.1")

    // Rate Limiting
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 3: settings.gradle.kts 작성**

```kotlin
rootProject.name = "linkly"
```

- [ ] **Step 4: application.yml 작성**

```yaml
spring:
  application:
    name: linkly
  datasource:
    url: jdbc:postgresql://localhost:5432/linkly
    username: linkly
    password: linkly
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID:placeholder}
            client-secret: ${GOOGLE_CLIENT_SECRET:placeholder}
            scope: openid,profile,email
          github:
            client-id: ${GITHUB_CLIENT_ID:placeholder}
            client-secret: ${GITHUB_CLIENT_SECRET:placeholder}
            scope: user:email

linkly:
  ip-hash-secret: ${IP_HASH_SECRET:default-dev-secret-change-in-prod}
  short-code-length: 6

server:
  port: 8080
```

- [ ] **Step 5: LinklyApplication.java 작성**

```java
package com.linkly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LinklyApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinklyApplication.class, args);
    }
}
```

- [ ] **Step 6: 테스트 application.yml (H2 사용) 작성**

Create: `src/test/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: false
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: test-google-id
            client-secret: test-google-secret
          github:
            client-id: test-github-id
            client-secret: test-github-secret

linkly:
  ip-hash-secret: test-secret
  short-code-length: 6
```

- [ ] **Step 7: 빌드 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Git 초기화 및 커밋**

```bash
git init
echo -e "build/\n.gradle/\n*.iml\n.idea/\nout/\n.superpowers/\n.firecrawl/\ntobyteam/" > .gitignore
git add build.gradle.kts settings.gradle.kts src/ .gitignore
git commit -m "feat: initialize Spring Boot 4 project with Gradle Kotlin DSL"
```

---

## Task 2: Flyway 마이그레이션 + 엔티티 정의

**Files:**
- Create: `src/main/resources/db/migration/V1__init_schema.sql`
- Create: `src/main/java/com/linkly/identity/domain/User.java`
- Create: `src/main/java/com/linkly/link/domain/Link.java`
- Create: `src/main/java/com/linkly/analytics/domain/ClickEvent.java`

- [ ] **Step 1: Flyway 마이그레이션 SQL 작성**

```sql
-- V1__init_schema.sql

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_provider UNIQUE (provider, provider_id)
);

CREATE TABLE links (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(20) NOT NULL,
    original_url TEXT NOT NULL,
    title VARCHAR(500),
    user_id BIGINT NOT NULL REFERENCES users(id),
    click_count BIGINT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_links_short_code UNIQUE (short_code)
);

CREATE INDEX idx_links_user_deleted ON links (user_id, deleted_at);

CREATE TABLE click_events (
    id BIGSERIAL PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES links(id),
    clicked_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ip_hash VARCHAR(64),
    user_agent TEXT,
    referer TEXT,
    device_type VARCHAR(20),
    browser VARCHAR(100)
);

CREATE INDEX idx_click_events_link_clicked ON click_events (link_id, clicked_at);
```

- [ ] **Step 2: User 엔티티 작성**

```java
package com.linkly.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(name = "uk_users_provider", columnNames = {"provider", "provider_id"})
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    public User(String email, String name, String provider, String providerId) {
        this.email = email;
        this.name = name;
        this.provider = provider;
        this.providerId = providerId;
    }

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getProvider() { return provider; }
    public String getProviderId() { return providerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateProfile(String email, String name) {
        this.email = email;
        this.name = name;
    }
}
```

- [ ] **Step 3: Link 엔티티 작성**

```java
package com.linkly.link.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "links")
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 20)
    private String shortCode;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(length = 500)
    private String title;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Link() {}

    public Link(String shortCode, String originalUrl, String title, Long userId) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.title = title;
        this.userId = userId;
    }

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getShortCode() { return shortCode; }
    public String getOriginalUrl() { return originalUrl; }
    public String getTitle() { return title; }
    public Long getUserId() { return userId; }
    public long getClickCount() { return clickCount; }
    public boolean isActive() { return active; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isDeleted() { return deletedAt != null; }
    public boolean isExpired() { return expiresAt != null && Instant.now().isAfter(expiresAt); }
    public boolean isAccessible() { return active && !isDeleted() && !isExpired(); }

    public void updateTitle(String title) { this.title = title; }
    public void setActive(boolean active) { this.active = active; }
    public void softDelete() { this.deletedAt = Instant.now(); }
    public void incrementClickCount() { this.clickCount++; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
```

- [ ] **Step 4: ClickEvent 엔티티 작성**

```java
package com.linkly.analytics.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "click_events")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String referer;

    @Column(name = "device_type", length = 20)
    private String deviceType;

    @Column(length = 100)
    private String browser;

    protected ClickEvent() {}

    public ClickEvent(Long linkId, Instant clickedAt, String ipHash,
                      String userAgent, String referer, String deviceType, String browser) {
        this.linkId = linkId;
        this.clickedAt = clickedAt;
        this.ipHash = ipHash;
        this.userAgent = userAgent;
        this.referer = referer;
        this.deviceType = deviceType;
        this.browser = browser;
    }

    public Long getId() { return id; }
    public Long getLinkId() { return linkId; }
    public Instant getClickedAt() { return clickedAt; }
    public String getIpHash() { return ipHash; }
    public String getUserAgent() { return userAgent; }
    public String getReferer() { return referer; }
    public String getDeviceType() { return deviceType; }
    public String getBrowser() { return browser; }
}
```

- [ ] **Step 5: 커밋**

```bash
git add src/main/resources/db/ src/main/java/com/linkly/identity/domain/ src/main/java/com/linkly/link/domain/ src/main/java/com/linkly/analytics/domain/
git commit -m "feat: add Flyway migration and JPA entities (User, Link, ClickEvent)"
```

---

## Task 3: Base62 인코더 + ShortCode 생성기 (TDD)

**Files:**
- Create: `src/main/java/com/linkly/link/shortcode/Base62Encoder.java`
- Create: `src/main/java/com/linkly/link/shortcode/ShortCodeGenerator.java`
- Create: `src/test/java/com/linkly/link/shortcode/Base62EncoderTest.java`
- Create: `src/test/java/com/linkly/link/shortcode/ShortCodeGeneratorTest.java`

- [ ] **Step 1: Base62Encoder 실패 테스트 작성**

```java
package com.linkly.link.shortcode;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class Base62EncoderTest {

    @Test
    void generateReturnsStringOfRequestedLength() {
        String code = Base62Encoder.generate(6);
        assertThat(code).hasSize(6);
    }

    @Test
    void generateOnlyContainsBase62Characters() {
        String code = Base62Encoder.generate(8);
        assertThat(code).matches("[a-zA-Z0-9]+");
    }

    @Test
    void generateProducesDifferentCodes() {
        String code1 = Base62Encoder.generate(6);
        String code2 = Base62Encoder.generate(6);
        assertThat(code1).isNotEqualTo(code2);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
./gradlew test --tests "com.linkly.link.shortcode.Base62EncoderTest" --info
```

Expected: FAIL — `Base62Encoder` 클래스 없음

- [ ] **Step 3: Base62Encoder 구현**

```java
package com.linkly.link.shortcode;

import java.security.SecureRandom;

public final class Base62Encoder {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Base62Encoder() {}

    public static String generate(int length) {
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.linkly.link.shortcode.Base62EncoderTest" --info
```

Expected: 3 tests PASSED

- [ ] **Step 5: ShortCodeGenerator 실패 테스트 작성**

```java
package com.linkly.link.shortcode;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator(6);

    @Test
    void generateReturnsBase62CodeOfConfiguredLength() {
        String code = generator.generate();
        assertThat(code).hasSize(6).matches("[a-zA-Z0-9]+");
    }
}
```

- [ ] **Step 6: ShortCodeGenerator 구현**

```java
package com.linkly.link.shortcode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {

    private final int length;

    public ShortCodeGenerator(@Value("${linkly.short-code-length:6}") int length) {
        this.length = length;
    }

    public String generate() {
        return Base62Encoder.generate(length);
    }
}
```

- [ ] **Step 7: 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.linkly.link.shortcode.*" --info
```

Expected: 4 tests PASSED

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/linkly/link/shortcode/ src/test/java/com/linkly/link/shortcode/
git commit -m "feat: add Base62Encoder and ShortCodeGenerator with tests"
```

---

## Task 4: IpHasher + UserAgentParser (TDD)

**Files:**
- Create: `src/main/java/com/linkly/analytics/privacy/IpHasher.java`
- Create: `src/main/java/com/linkly/analytics/enrichment/UserAgentParser.java`
- Create: `src/test/java/com/linkly/analytics/privacy/IpHasherTest.java`
- Create: `src/test/java/com/linkly/analytics/enrichment/UserAgentParserTest.java`

- [ ] **Step 1: IpHasher 실패 테스트 작성**

```java
package com.linkly.analytics.privacy;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IpHasherTest {

    private final IpHasher hasher = new IpHasher("test-secret");

    @Test
    void hashReturnsDeterministicResult() {
        String hash1 = hasher.hash("192.168.1.1");
        String hash2 = hasher.hash("192.168.1.1");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hashReturnsDifferentResultForDifferentIps() {
        String hash1 = hasher.hash("192.168.1.1");
        String hash2 = hasher.hash("10.0.0.1");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hashReturns64CharHexString() {
        String hash = hasher.hash("192.168.1.1");
        assertThat(hash).hasSize(64).matches("[a-f0-9]+");
    }

    @Test
    void hashDiffersWithDifferentSecrets() {
        var otherHasher = new IpHasher("other-secret");
        assertThat(hasher.hash("192.168.1.1"))
            .isNotEqualTo(otherHasher.hash("192.168.1.1"));
    }
}
```

- [ ] **Step 2: IpHasher 구현**

```java
package com.linkly.analytics.privacy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Component
public class IpHasher {

    private final String secret;

    public IpHasher(@Value("${linkly.ip-hash-secret}") String secret) {
        this.secret = secret;
    }

    public String hash(String ip) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] result = mac.doFinal(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }
}
```

- [ ] **Step 3: IpHasher 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.linkly.analytics.privacy.IpHasherTest" --info
```

Expected: 4 tests PASSED

- [ ] **Step 4: UserAgentParser 실패 테스트 작성**

```java
package com.linkly.analytics.enrichment;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UserAgentParserTest {

    private final UserAgentParser parser = new UserAgentParser();

    @Test
    void parseChromeDesktop() {
        var result = parser.parse("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        assertThat(result.browser()).isEqualTo("Chrome");
        assertThat(result.deviceType()).isEqualTo("desktop");
    }

    @Test
    void parseSafariMobile() {
        var result = parser.parse("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");
        assertThat(result.browser()).isEqualTo("Mobile Safari");
        assertThat(result.deviceType()).isEqualTo("mobile");
    }

    @Test
    void parseNullUserAgent() {
        var result = parser.parse(null);
        assertThat(result.browser()).isEqualTo("Unknown");
        assertThat(result.deviceType()).isEqualTo("unknown");
    }
}
```

- [ ] **Step 5: UserAgentParser 구현**

```java
package com.linkly.analytics.enrichment;

import org.springframework.stereotype.Component;
import ua_parser.Client;
import ua_parser.Parser;

@Component
public class UserAgentParser {

    private final Parser parser = new Parser();

    public record ParsedUA(String browser, String deviceType) {}

    public ParsedUA parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new ParsedUA("Unknown", "unknown");
        }
        Client client = parser.parse(userAgent);
        String browser = client.userAgent.family;
        String deviceType = inferDeviceType(client.device.family, userAgent);
        return new ParsedUA(browser, deviceType);
    }

    private String inferDeviceType(String deviceFamily, String userAgent) {
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("android")) {
            return "mobile";
        }
        if (ua.contains("tablet") || ua.contains("ipad")) {
            return "tablet";
        }
        return "desktop";
    }
}
```

- [ ] **Step 6: UserAgentParser 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.linkly.analytics.enrichment.UserAgentParserTest" --info
```

Expected: 3 tests PASSED

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/linkly/analytics/privacy/ src/main/java/com/linkly/analytics/enrichment/ src/test/java/com/linkly/analytics/
git commit -m "feat: add IpHasher (HMAC-SHA256) and UserAgentParser with tests"
```

---

## Task 5: Repository 계층

**Files:**
- Create: `src/main/java/com/linkly/identity/repository/UserRepository.java`
- Create: `src/main/java/com/linkly/link/repository/LinkRepository.java`
- Create: `src/main/java/com/linkly/analytics/repository/ClickEventRepository.java`

- [ ] **Step 1: UserRepository 작성**

```java
package com.linkly.identity.repository;

import com.linkly.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
}
```

- [ ] **Step 2: LinkRepository 작성**

```java
package com.linkly.link.repository;

import com.linkly.link.domain.Link;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    Page<Link> findByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

    @Modifying
    @Query("UPDATE Link l SET l.clickCount = l.clickCount + 1 WHERE l.id = :linkId")
    void incrementClickCount(Long linkId);
}
```

- [ ] **Step 3: ClickEventRepository 작성**

```java
package com.linkly.analytics.repository;

import com.linkly.analytics.domain.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByLinkIdAndClickedAtBetween(Long linkId, Instant from, Instant to);

    @Query("""
        SELECT CAST(ce.clickedAt AS DATE) as date, COUNT(ce) as clicks
        FROM ClickEvent ce
        WHERE ce.linkId = :linkId AND ce.clickedAt BETWEEN :from AND :to
        GROUP BY CAST(ce.clickedAt AS DATE)
        ORDER BY date
        """)
    List<Object[]> countByLinkIdGroupByDate(Long linkId, Instant from, Instant to);

    @Query("""
        SELECT COUNT(DISTINCT CONCAT(ce.ipHash, ce.userAgent, CAST(ce.clickedAt AS DATE)))
        FROM ClickEvent ce
        WHERE ce.linkId = :linkId AND ce.clickedAt BETWEEN :from AND :to
        """)
    long countUniqueClicks(Long linkId, Instant from, Instant to);

    @Query("SELECT ce.referer, COUNT(ce) FROM ClickEvent ce WHERE ce.linkId = :linkId AND ce.clickedAt BETWEEN :from AND :to AND ce.referer IS NOT NULL GROUP BY ce.referer ORDER BY COUNT(ce) DESC")
    List<Object[]> findTopReferers(Long linkId, Instant from, Instant to);

    @Query("SELECT ce.deviceType, COUNT(ce) FROM ClickEvent ce WHERE ce.linkId = :linkId AND ce.clickedAt BETWEEN :from AND :to AND ce.deviceType IS NOT NULL GROUP BY ce.deviceType")
    List<Object[]> findDeviceBreakdown(Long linkId, Instant from, Instant to);

    @Query("SELECT ce.browser, COUNT(ce) FROM ClickEvent ce WHERE ce.linkId = :linkId AND ce.clickedAt BETWEEN :from AND :to AND ce.browser IS NOT NULL GROUP BY ce.browser ORDER BY COUNT(ce) DESC")
    List<Object[]> findBrowserBreakdown(Long linkId, Instant from, Instant to);

    List<ClickEvent> findTop10ByLinkIdOrderByClickedAtDesc(Long linkId);
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/linkly/identity/repository/ src/main/java/com/linkly/link/repository/ src/main/java/com/linkly/analytics/repository/
git commit -m "feat: add JPA repositories for User, Link, ClickEvent"
```

---

## Task 6: AsyncConfig + ClickEvent 비동기 파이프라인

**Files:**
- Create: `src/main/java/com/linkly/config/AsyncConfig.java`
- Create: `src/main/java/com/linkly/analytics/event/ClickEventPayload.java`
- Create: `src/main/java/com/linkly/analytics/event/ClickEventListener.java`

- [ ] **Step 1: AsyncConfig 작성**

```java
package com.linkly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "analyticsExecutor")
    public Executor analyticsExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("analytics-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 2: ClickEventPayload 작성**

```java
package com.linkly.analytics.event;

import java.time.Instant;

public record ClickEventPayload(
    Long linkId,
    Instant clickedAt,
    String ipAddress,
    String userAgent,
    String referer
) {}
```

- [ ] **Step 3: ClickEventListener 작성**

```java
package com.linkly.analytics.event;

import com.linkly.analytics.domain.ClickEvent;
import com.linkly.analytics.enrichment.UserAgentParser;
import com.linkly.analytics.privacy.IpHasher;
import com.linkly.analytics.repository.ClickEventRepository;
import com.linkly.link.repository.LinkRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ClickEventListener {

    private final ClickEventRepository clickEventRepository;
    private final LinkRepository linkRepository;
    private final IpHasher ipHasher;
    private final UserAgentParser userAgentParser;

    public ClickEventListener(ClickEventRepository clickEventRepository,
                              LinkRepository linkRepository,
                              IpHasher ipHasher,
                              UserAgentParser userAgentParser) {
        this.clickEventRepository = clickEventRepository;
        this.linkRepository = linkRepository;
        this.ipHasher = ipHasher;
        this.userAgentParser = userAgentParser;
    }

    @Async("analyticsExecutor")
    @EventListener
    @Transactional
    public void handleClickEvent(ClickEventPayload payload) {
        var parsedUA = userAgentParser.parse(payload.userAgent());

        var clickEvent = new ClickEvent(
            payload.linkId(),
            payload.clickedAt(),
            payload.ipAddress() != null ? ipHasher.hash(payload.ipAddress()) : null,
            payload.userAgent(),
            payload.referer(),
            parsedUA.deviceType(),
            parsedUA.browser()
        );

        clickEventRepository.save(clickEvent);
        linkRepository.incrementClickCount(payload.linkId());
    }
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/linkly/config/AsyncConfig.java src/main/java/com/linkly/analytics/event/
git commit -m "feat: add async click event pipeline with IP hashing and UA parsing"
```

---

## Task 7: LinkService + 테스트

**Files:**
- Create: `src/main/java/com/linkly/link/service/LinkService.java`
- Create: `src/test/java/com/linkly/link/service/LinkServiceTest.java`

- [ ] **Step 1: LinkService 실패 테스트 작성**

```java
package com.linkly.link.service;

import com.linkly.link.domain.Link;
import com.linkly.link.repository.LinkRepository;
import com.linkly.link.shortcode.ShortCodeGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock LinkRepository linkRepository;
    @Mock ShortCodeGenerator shortCodeGenerator;
    @InjectMocks LinkService linkService;

    @Test
    void createLinkGeneratesShortCodeAndSaves() {
        given(shortCodeGenerator.generate()).willReturn("abc123");
        given(linkRepository.existsByShortCode("abc123")).willReturn(false);
        given(linkRepository.save(any(Link.class))).willAnswer(inv -> inv.getArgument(0));

        Link link = linkService.createLink("https://example.com", "Test", 1L, null);

        assertThat(link.getShortCode()).isEqualTo("abc123");
        assertThat(link.getOriginalUrl()).isEqualTo("https://example.com");
        assertThat(link.getUserId()).isEqualTo(1L);
    }

    @Test
    void createLinkRetriesOnCollision() {
        given(shortCodeGenerator.generate()).willReturn("aaa111", "bbb222");
        given(linkRepository.existsByShortCode("aaa111")).willReturn(true);
        given(linkRepository.existsByShortCode("bbb222")).willReturn(false);
        given(linkRepository.save(any(Link.class))).willAnswer(inv -> inv.getArgument(0));

        Link link = linkService.createLink("https://example.com", null, 1L, null);

        assertThat(link.getShortCode()).isEqualTo("bbb222");
    }

    @Test
    void findByShortCodeReturnsAccessibleLink() {
        var link = new Link("abc123", "https://example.com", null, 1L);
        given(linkRepository.findByShortCode("abc123")).willReturn(Optional.of(link));

        Optional<Link> result = linkService.findAccessibleByShortCode("abc123");

        assertThat(result).isPresent();
    }

    @Test
    void findByShortCodeReturnsEmptyForDeletedLink() {
        var link = new Link("abc123", "https://example.com", null, 1L);
        link.softDelete();
        given(linkRepository.findByShortCode("abc123")).willReturn(Optional.of(link));

        Optional<Link> result = linkService.findAccessibleByShortCode("abc123");

        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
./gradlew test --tests "com.linkly.link.service.LinkServiceTest" --info
```

Expected: FAIL — `LinkService` 없음

- [ ] **Step 3: LinkService 구현**

```java
package com.linkly.link.service;

import com.linkly.link.domain.Link;
import com.linkly.link.repository.LinkRepository;
import com.linkly.link.shortcode.ShortCodeGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class LinkService {

    private static final int MAX_RETRY = 10;

    private final LinkRepository linkRepository;
    private final ShortCodeGenerator shortCodeGenerator;

    public LinkService(LinkRepository linkRepository, ShortCodeGenerator shortCodeGenerator) {
        this.linkRepository = linkRepository;
        this.shortCodeGenerator = shortCodeGenerator;
    }

    @Transactional
    public Link createLink(String originalUrl, String title, Long userId, Instant expiresAt) {
        String shortCode = generateUniqueShortCode();
        var link = new Link(shortCode, originalUrl, title, userId);
        if (expiresAt != null) {
            link.setExpiresAt(expiresAt);
        }
        return linkRepository.save(link);
    }

    public Optional<Link> findAccessibleByShortCode(String shortCode) {
        return linkRepository.findByShortCode(shortCode)
            .filter(Link::isAccessible);
    }

    public Optional<Link> findByShortCode(String shortCode) {
        return linkRepository.findByShortCode(shortCode);
    }

    public Optional<Link> findByIdAndUserId(Long id, Long userId) {
        return linkRepository.findById(id)
            .filter(link -> link.getUserId().equals(userId) && !link.isDeleted());
    }

    public Page<Link> findByUserId(Long userId, Pageable pageable) {
        return linkRepository.findByUserIdAndDeletedAtIsNull(userId, pageable);
    }

    @Transactional
    public Link updateLink(Long id, Long userId, String title, Boolean active) {
        var link = linkRepository.findById(id)
            .filter(l -> l.getUserId().equals(userId) && !l.isDeleted())
            .orElseThrow(() -> new LinkNotFoundException(id));
        if (title != null) link.updateTitle(title);
        if (active != null) link.setActive(active);
        return link;
    }

    @Transactional
    public void deleteLink(Long id, Long userId) {
        var link = linkRepository.findById(id)
            .filter(l -> l.getUserId().equals(userId) && !l.isDeleted())
            .orElseThrow(() -> new LinkNotFoundException(id));
        link.softDelete();
    }

    private String generateUniqueShortCode() {
        for (int i = 0; i < MAX_RETRY; i++) {
            String code = shortCodeGenerator.generate();
            if (!linkRepository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to generate unique short code after " + MAX_RETRY + " attempts");
    }

    public static class LinkNotFoundException extends RuntimeException {
        public LinkNotFoundException(Long id) {
            super("Link not found: " + id);
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.linkly.link.service.LinkServiceTest" --info
```

Expected: 4 tests PASSED

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/linkly/link/service/ src/test/java/com/linkly/link/service/
git commit -m "feat: add LinkService with short code generation, CRUD, and soft delete"
```

---

## Task 8: SecurityConfig + OAuth2 사용자 서비스

**Files:**
- Create: `src/main/java/com/linkly/config/SecurityConfig.java`
- Create: `src/main/java/com/linkly/identity/service/CustomOAuth2UserService.java`

- [ ] **Step 1: CustomOAuth2UserService 작성**

```java
package com.linkly.identity.service;

import com.linkly.identity.domain.User;
import com.linkly.identity.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        String providerId = oAuth2User.getName();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        if (name == null) name = oAuth2User.getAttribute("login"); // GitHub fallback

        userRepository.findByProviderAndProviderId(provider, providerId)
            .ifPresentOrElse(
                existing -> existing.updateProfile(email, name != null ? name : existing.getName()),
                () -> userRepository.save(new User(email, name != null ? name : "User", provider, providerId))
            );

        return oAuth2User;
    }
}
```

- [ ] **Step 2: SecurityConfig 작성**

```java
package com.linkly.config;

import com.linkly.identity.service.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService) {
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public: redirect, static, login, health
                .requestMatchers("/app/login", "/error", "/static/**").permitAll()
                // Public: redirect endpoint handled by RedirectController
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/app/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth -> oauth
                .loginPage("/app/login")
                .defaultSuccessUrl("/app/dashboard", true)
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
            )
            .logout(logout -> logout
                .logoutUrl("/app/logout")
                .logoutSuccessUrl("/app/login?logout")
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            );
        return http.build();
    }
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/linkly/config/SecurityConfig.java src/main/java/com/linkly/identity/service/
git commit -m "feat: add Spring Security OAuth2 config with custom user service"
```

---

## Task 9: RedirectController + 테스트

**Files:**
- Create: `src/main/java/com/linkly/redirect/web/RedirectController.java`
- Create: `src/test/java/com/linkly/redirect/web/RedirectControllerTest.java`

- [ ] **Step 1: RedirectController 실패 통합 테스트 작성**

```java
package com.linkly.redirect.web;

import com.linkly.link.domain.Link;
import com.linkly.link.repository.LinkRepository;
import com.linkly.identity.domain.User;
import com.linkly.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RedirectControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired LinkRepository linkRepository;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void setUp() {
        linkRepository.deleteAll();
        userRepository.deleteAll();
        var user = userRepository.save(new User("test@example.com", "Test", "google", "123"));
        linkRepository.save(new Link("abc123", "https://example.com", null, user.getId()));
    }

    @Test
    void redirectsToOriginalUrl() throws Exception {
        mockMvc.perform(get("/abc123"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("https://example.com"));
    }

    @Test
    void returns404ForUnknownCode() throws Exception {
        mockMvc.perform(get("/unknown"))
            .andExpect(status().isNotFound());
    }

    @Test
    void returns410ForDeletedLink() throws Exception {
        var link = linkRepository.findByShortCode("abc123").orElseThrow();
        link.softDelete();
        linkRepository.save(link);

        mockMvc.perform(get("/abc123"))
            .andExpect(status().isGone());
    }
}
```

- [ ] **Step 2: RedirectController 구현**

```java
package com.linkly.redirect.web;

import com.linkly.analytics.event.ClickEventPayload;
import com.linkly.link.domain.Link;
import com.linkly.link.repository.LinkRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.ErrorResponseException;

import java.net.URI;
import java.time.Instant;

@Controller
public class RedirectController {

    private final LinkRepository linkRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RedirectController(LinkRepository linkRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.linkRepository = linkRepository;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9]{4,20}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        var link = linkRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new ErrorResponseException(HttpStatus.NOT_FOUND));

        if (link.isDeleted() || link.isExpired()) {
            throw new ErrorResponseException(HttpStatus.GONE);
        }
        if (!link.isActive()) {
            throw new ErrorResponseException(HttpStatus.NOT_FOUND);
        }

        eventPublisher.publishEvent(new ClickEventPayload(
            link.getId(),
            Instant.now(),
            request.getRemoteAddr(),
            request.getHeader(HttpHeaders.USER_AGENT),
            request.getHeader(HttpHeaders.REFERER)
        ));

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(link.getOriginalUrl()))
            .build();
    }
}
```

- [ ] **Step 3: 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.linkly.redirect.web.RedirectControllerTest" --info
```

Expected: 3 tests PASSED

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/linkly/redirect/ src/test/java/com/linkly/redirect/
git commit -m "feat: add RedirectController with 302 redirect and async click event"
```

---

## Task 10: Link API Controller + 테스트

**Files:**
- Create: `src/main/java/com/linkly/link/api/LinkApiController.java`
- Create: `src/test/java/com/linkly/link/api/LinkApiControllerTest.java`

- [ ] **Step 1: LinkApiController 실패 테스트 작성**

```java
package com.linkly.link.api;

import com.linkly.identity.domain.User;
import com.linkly.identity.repository.UserRepository;
import com.linkly.link.repository.LinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LinkApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired LinkRepository linkRepository;
    @Autowired UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        linkRepository.deleteAll();
        userRepository.deleteAll();
        testUser = userRepository.save(new User("test@example.com", "Test", "google", "123"));
    }

    @Test
    void createLinkReturnsCreated() throws Exception {
        mockMvc.perform(post("/api/links")
                .with(oauth2Login().attributes(attrs -> attrs.put("sub", "123")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"url": "https://example.com", "title": "Example"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shortCode").isNotEmpty())
            .andExpect(jsonPath("$.originalUrl").value("https://example.com"));
    }

    @Test
    void createLinkRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"url": "https://example.com"}
                    """))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: LinkApiController 구현**

```java
package com.linkly.link.api;

import com.linkly.identity.repository.UserRepository;
import com.linkly.link.domain.Link;
import com.linkly.link.service.LinkService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/links")
public class LinkApiController {

    private final LinkService linkService;
    private final UserRepository userRepository;

    public LinkApiController(LinkService linkService, UserRepository userRepository) {
        this.linkService = linkService;
        this.userRepository = userRepository;
    }

    record CreateLinkRequest(@NotBlank @URL String url, String title, Instant expiresAt) {}

    record UpdateLinkRequest(String title, Boolean active) {}

    record LinkResponse(Long id, String shortCode, String originalUrl, String title,
                        long clickCount, boolean active, Instant expiresAt,
                        Instant createdAt) {
        static LinkResponse from(Link link) {
            return new LinkResponse(link.getId(), link.getShortCode(), link.getOriginalUrl(),
                link.getTitle(), link.getClickCount(), link.isActive(),
                link.getExpiresAt(), link.getCreatedAt());
        }
    }

    @PostMapping
    public ResponseEntity<LinkResponse> createLink(@Valid @RequestBody CreateLinkRequest request,
                                                   @AuthenticationPrincipal OAuth2User principal) {
        Long userId = resolveUserId(principal);
        Link link = linkService.createLink(request.url(), request.title(), userId, request.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(LinkResponse.from(link));
    }

    @GetMapping
    public Page<LinkResponse> listLinks(@AuthenticationPrincipal OAuth2User principal, Pageable pageable) {
        Long userId = resolveUserId(principal);
        return linkService.findByUserId(userId, pageable).map(LinkResponse::from);
    }

    @GetMapping("/{id}")
    public LinkResponse getLink(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal) {
        Long userId = resolveUserId(principal);
        return linkService.findByIdAndUserId(id, userId)
            .map(LinkResponse::from)
            .orElseThrow(() -> new ErrorResponseException(HttpStatus.NOT_FOUND));
    }

    @PatchMapping("/{id}")
    public LinkResponse updateLink(@PathVariable Long id,
                                   @RequestBody UpdateLinkRequest request,
                                   @AuthenticationPrincipal OAuth2User principal) {
        Long userId = resolveUserId(principal);
        Link link = linkService.updateLink(id, userId, request.title(), request.active());
        return LinkResponse.from(link);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLink(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal) {
        Long userId = resolveUserId(principal);
        linkService.deleteLink(id, userId);
    }

    private Long resolveUserId(OAuth2User principal) {
        String providerId = principal.getName();
        return userRepository.findByProviderAndProviderId("google", providerId)
            .or(() -> userRepository.findByProviderAndProviderId("github", providerId))
            .orElseThrow(() -> new ErrorResponseException(HttpStatus.UNAUTHORIZED))
            .getId();
    }
}
```

- [ ] **Step 3: 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.linkly.link.api.LinkApiControllerTest" --info
```

Expected: 2 tests PASSED

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/linkly/link/api/ src/test/java/com/linkly/link/api/
git commit -m "feat: add Link REST API controller with CRUD operations"
```

---

## Task 11: AnalyticsService + API Controller (v1/v2)

**Files:**
- Create: `src/main/java/com/linkly/analytics/service/AnalyticsService.java`
- Create: `src/main/java/com/linkly/analytics/api/AnalyticsApiController.java`
- Create: `src/main/java/com/linkly/config/WebMvcConfig.java`
- Create: `src/test/java/com/linkly/analytics/api/AnalyticsApiControllerTest.java`

- [ ] **Step 1: WebMvcConfig — API Versioning 설정**

```java
package com.linkly.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
            .useRequestHeader("X-API-Version")
            .defaultVersion("1");
    }
}
```

Note: Spring Framework 7의 정확한 API versioning 설정은 릴리스 시점에 따라 메서드 시그니처가 다를 수 있다. 컴파일 시 오류가 발생하면 공식 문서(`https://docs.spring.io/spring-framework/reference/7.0/web/webmvc/mvc-config/api-version.html`)를 참고하여 조정한다.

- [ ] **Step 2: AnalyticsService 작성**

```java
package com.linkly.analytics.service;

import com.linkly.analytics.repository.ClickEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final ClickEventRepository clickEventRepository;

    public AnalyticsService(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    public record DateCount(String date, long clicks) {}

    public record StatsV1(long totalClicks, long uniqueClicks, List<DateCount> clicksByDate) {}

    public record StatsV2(long totalClicks, long uniqueClicks, List<DateCount> clicksByDate,
                          List<String> topReferers, Map<String, Long> deviceBreakdown,
                          Map<String, Long> browserBreakdown) {}

    public StatsV1 getStatsV1(Long linkId, Instant from, Instant to) {
        long total = clickEventRepository.countByLinkIdAndClickedAtBetween(linkId, from, to);
        long unique = clickEventRepository.countUniqueClicks(linkId, from, to);
        var byDate = clickEventRepository.countByLinkIdGroupByDate(linkId, from, to).stream()
            .map(row -> new DateCount(row[0].toString(), ((Number) row[1]).longValue()))
            .toList();
        return new StatsV1(total, unique, byDate);
    }

    public StatsV2 getStatsV2(Long linkId, Instant from, Instant to) {
        var v1 = getStatsV1(linkId, from, to);

        var topReferers = clickEventRepository.findTopReferers(linkId, from, to).stream()
            .limit(10)
            .map(row -> (String) row[0])
            .toList();

        var deviceBreakdown = clickEventRepository.findDeviceBreakdown(linkId, from, to).stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).longValue(),
                (a, b) -> a,
                LinkedHashMap::new
            ));

        var browserBreakdown = clickEventRepository.findBrowserBreakdown(linkId, from, to).stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).longValue(),
                (a, b) -> a,
                LinkedHashMap::new
            ));

        return new StatsV2(v1.totalClicks(), v1.uniqueClicks(), v1.clicksByDate(),
            topReferers, deviceBreakdown, browserBreakdown);
    }
}
```

- [ ] **Step 3: AnalyticsApiController 작성**

```java
package com.linkly.analytics.api;

import com.linkly.analytics.service.AnalyticsService;
import com.linkly.identity.repository.UserRepository;
import com.linkly.link.service.LinkService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/links/{linkId}/stats")
public class AnalyticsApiController {

    private final AnalyticsService analyticsService;
    private final LinkService linkService;
    private final UserRepository userRepository;

    public AnalyticsApiController(AnalyticsService analyticsService,
                                  LinkService linkService,
                                  UserRepository userRepository) {
        this.analyticsService = analyticsService;
        this.linkService = linkService;
        this.userRepository = userRepository;
    }

    @GetMapping(version = "1")
    public AnalyticsService.StatsV1 getStatsV1(
            @PathVariable Long linkId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal OAuth2User principal) {
        verifyOwnership(linkId, principal);
        return analyticsService.getStatsV1(linkId,
            from.atStartOfDay().toInstant(ZoneOffset.UTC),
            to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    @GetMapping(version = "2")
    public AnalyticsService.StatsV2 getStatsV2(
            @PathVariable Long linkId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal OAuth2User principal) {
        verifyOwnership(linkId, principal);
        return analyticsService.getStatsV2(linkId,
            from.atStartOfDay().toInstant(ZoneOffset.UTC),
            to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private void verifyOwnership(Long linkId, OAuth2User principal) {
        String providerId = principal.getName();
        var user = userRepository.findByProviderAndProviderId("google", providerId)
            .or(() -> userRepository.findByProviderAndProviderId("github", providerId))
            .orElseThrow(() -> new ErrorResponseException(HttpStatus.UNAUTHORIZED));
        linkService.findByIdAndUserId(linkId, user.getId())
            .orElseThrow(() -> new ErrorResponseException(HttpStatus.NOT_FOUND));
    }
}
```

Note: `@GetMapping(version = "1")` 문법은 Spring Framework 7의 API versioning feature에 의존한다. 만약 컴파일 오류가 발생하면, `version` attribute 대신 별도의 `@ApiVersion` 어노테이션이나 `@RequestMapping(headers = "X-API-Version=1")` 폴백을 사용한다.

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/linkly/config/WebMvcConfig.java src/main/java/com/linkly/analytics/service/ src/main/java/com/linkly/analytics/api/
git commit -m "feat: add Analytics API with v1/v2 versioning using Spring Framework 7"
```

---

## Task 12: QR 코드 서비스 + API (TDD)

**Files:**
- Create: `src/main/java/com/linkly/qr/service/QrCodeService.java`
- Create: `src/test/java/com/linkly/qr/service/QrCodeServiceTest.java`

- [ ] **Step 1: QrCodeService 실패 테스트 작성**

```java
package com.linkly.qr.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class QrCodeServiceTest {

    private final QrCodeService qrCodeService = new QrCodeService();

    @Test
    void generateReturnsNonEmptyPngBytes() throws Exception {
        byte[] png = qrCodeService.generateQrCode("https://example.com", 200);
        assertThat(png).isNotEmpty();
        // PNG magic bytes: 0x89 0x50 0x4E 0x47
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(png[1]).isEqualTo((byte) 0x50);
    }

    @Test
    void generateRespectsSizeParameter() throws Exception {
        byte[] small = qrCodeService.generateQrCode("https://example.com", 100);
        byte[] large = qrCodeService.generateQrCode("https://example.com", 400);
        assertThat(large.length).isGreaterThan(small.length);
    }
}
```

- [ ] **Step 2: QrCodeService 구현**

```java
package com.linkly.qr.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class QrCodeService {

    public byte[] generateQrCode(String url, int size) throws Exception {
        var writer = new QRCodeWriter();
        var bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size);
        var outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        return outputStream.toByteArray();
    }
}
```

- [ ] **Step 3: 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.linkly.qr.service.QrCodeServiceTest" --info
```

Expected: 2 tests PASSED

- [ ] **Step 4: LinkApiController에 QR 엔드포인트 추가**

`LinkApiController.java`에 다음 메서드를 추가한다:

```java
@GetMapping("/{id}/qr")
public ResponseEntity<byte[]> getQrCode(@PathVariable Long id,
                                        @RequestParam(defaultValue = "200") int size,
                                        @AuthenticationPrincipal OAuth2User principal) throws Exception {
    Long userId = resolveUserId(principal);
    var link = linkService.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ErrorResponseException(HttpStatus.NOT_FOUND));

    byte[] qrImage = qrCodeService.generateQrCode(
        "http://localhost:8080/" + link.getShortCode(), size);

    String etag = "\"qr-" + link.getShortCode() + "-" + size + "\"";

    return ResponseEntity.ok()
        .header("Content-Type", "image/png")
        .header("Cache-Control", "public, max-age=86400")
        .header("ETag", etag)
        .body(qrImage);
}
```

QrCodeService를 컨트롤러의 생성자에 주입 추가:

```java
private final QrCodeService qrCodeService;

public LinkApiController(LinkService linkService, UserRepository userRepository, QrCodeService qrCodeService) {
    this.linkService = linkService;
    this.userRepository = userRepository;
    this.qrCodeService = qrCodeService;
}
```

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/linkly/qr/ src/test/java/com/linkly/qr/ src/main/java/com/linkly/link/api/LinkApiController.java
git commit -m "feat: add QR code generation with caching headers"
```

---

## Task 13: Rate Limiting (Bucket4j)

**Files:**
- Create: `src/main/java/com/linkly/config/RateLimitConfig.java`

- [ ] **Step 1: RateLimitConfig 작성**

```java
package com.linkly.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Configuration
public class RateLimitConfig {

    private final ConcurrentMap<String, Bucket> cache = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .<String, Bucket>build()
        .asMap();

    @Bean
    public FilterRegistrationBean<Filter> rateLimitFilter() {
        var registration = new FilterRegistrationBean<Filter>();
        registration.setFilter(new RateLimitFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }

    private class RateLimitFilter implements Filter {
        @Override
        public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
                throws IOException, ServletException {
            var request = (HttpServletRequest) req;
            var response = (HttpServletResponse) resp;

            String key = resolveKey(request);
            boolean authenticated = request.getUserPrincipal() != null;
            Bucket bucket = cache.computeIfAbsent(key, k -> createBucket(authenticated));

            if (bucket.tryConsume(1)) {
                chain.doFilter(req, resp);
            } else {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("application/problem+json");
                response.getWriter().write("""
                    {"type":"about:blank","title":"Too Many Requests","status":429,"detail":"Rate limit exceeded"}
                    """);
            }
        }

        private String resolveKey(HttpServletRequest request) {
            if (request.getUserPrincipal() != null) {
                return "user:" + request.getUserPrincipal().getName();
            }
            return "ip:" + request.getRemoteAddr();
        }

        private Bucket createBucket(boolean authenticated) {
            var limit = authenticated
                ? Bandwidth.simple(100, Duration.ofMinutes(1))
                : Bandwidth.simple(10, Duration.ofMinutes(1));
            return Bucket.builder().addLimit(limit).build();
        }
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/linkly/config/RateLimitConfig.java
git commit -m "feat: add Bucket4j rate limiting (10/min unauthenticated, 100/min authenticated)"
```

---

## Task 14: AuthApiController

**Files:**
- Create: `src/main/java/com/linkly/identity/api/AuthApiController.java`

- [ ] **Step 1: AuthApiController 작성**

```java
package com.linkly.identity.api;

import com.linkly.identity.domain.User;
import com.linkly.identity.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final UserRepository userRepository;

    public AuthApiController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    record UserResponse(Long id, String email, String name, String provider) {}

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal OAuth2User principal) {
        String providerId = principal.getName();
        User user = userRepository.findByProviderAndProviderId("google", providerId)
            .or(() -> userRepository.findByProviderAndProviderId("github", providerId))
            .orElseThrow(() -> new ErrorResponseException(HttpStatus.UNAUTHORIZED));
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getProvider());
    }
}
```

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/linkly/identity/api/
git commit -m "feat: add /api/auth/me endpoint"
```

---

## Task 15: Thymeleaf 웹 UI

**Files:**
- Create: `src/main/java/com/linkly/web/view/LoginViewController.java`
- Create: `src/main/java/com/linkly/web/view/DashboardViewController.java`
- Create: `src/main/resources/templates/login.html`
- Create: `src/main/resources/templates/dashboard.html`
- Create: `src/main/resources/templates/link-create.html`
- Create: `src/main/resources/templates/link-detail.html`

- [ ] **Step 1: LoginViewController 작성**

```java
package com.linkly.web.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginViewController {

    @GetMapping("/app/login")
    public String login() {
        return "login";
    }
}
```

- [ ] **Step 2: DashboardViewController 작성**

```java
package com.linkly.web.view;

import com.linkly.identity.repository.UserRepository;
import com.linkly.link.service.LinkService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/app")
public class DashboardViewController {

    private final LinkService linkService;
    private final UserRepository userRepository;

    public DashboardViewController(LinkService linkService, UserRepository userRepository) {
        this.linkService = linkService;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal OAuth2User principal, Model model,
                           @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = resolveUserId(principal);
        var links = linkService.findByUserId(userId, pageable);
        model.addAttribute("links", links);
        model.addAttribute("userName", principal.getAttribute("name"));
        return "dashboard";
    }

    @GetMapping("/links/new")
    public String createForm() {
        return "link-create";
    }

    @PostMapping("/links")
    public String createLink(@RequestParam String url, @RequestParam(required = false) String title,
                            @AuthenticationPrincipal OAuth2User principal) {
        Long userId = resolveUserId(principal);
        linkService.createLink(url, title, userId, null);
        return "redirect:/app/dashboard";
    }

    @GetMapping("/links/{id}")
    public String linkDetail(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal, Model model) {
        Long userId = resolveUserId(principal);
        var link = linkService.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new IllegalArgumentException("Link not found"));
        model.addAttribute("link", link);
        return "link-detail";
    }

    private Long resolveUserId(OAuth2User principal) {
        String providerId = principal.getName();
        return userRepository.findByProviderAndProviderId("google", providerId)
            .or(() -> userRepository.findByProviderAndProviderId("github", providerId))
            .orElseThrow()
            .getId();
    }
}
```

- [ ] **Step 3: login.html 작성**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Linkly - Login</title>
    <style>
        body { font-family: system-ui; max-width: 400px; margin: 100px auto; text-align: center; }
        h1 { color: #1a1a2e; }
        .login-btn { display: block; padding: 12px; margin: 10px 0; text-decoration: none;
                     border-radius: 8px; font-size: 16px; color: white; }
        .google { background: #4285f4; }
        .github { background: #333; }
    </style>
</head>
<body>
    <h1>Linkly</h1>
    <p>URL Shortener + Analytics</p>
    <a class="login-btn google" href="/oauth2/authorization/google">Google로 로그인</a>
    <a class="login-btn github" href="/oauth2/authorization/github">GitHub로 로그인</a>
</body>
</html>
```

- [ ] **Step 4: dashboard.html 작성**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Linkly - Dashboard</title>
    <style>
        body { font-family: system-ui; max-width: 800px; margin: 0 auto; padding: 20px; }
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 10px; border-bottom: 1px solid #eee; text-align: left; }
        .btn { padding: 8px 16px; background: #3b82f6; color: white; text-decoration: none; border-radius: 6px; }
        nav { display: flex; justify-content: space-between; align-items: center; margin-bottom: 30px; }
    </style>
</head>
<body>
    <nav>
        <h1>Linkly</h1>
        <div>
            <span th:text="${userName}">User</span> |
            <form th:action="@{/app/logout}" method="post" style="display:inline">
                <button type="submit" style="background:none;border:none;color:#3b82f6;cursor:pointer">로그아웃</button>
            </form>
        </div>
    </nav>
    <a class="btn" th:href="@{/app/links/new}">+ 새 링크 만들기</a>
    <table style="margin-top: 20px;">
        <thead><tr><th>제목</th><th>단축 URL</th><th>클릭</th><th>생성일</th></tr></thead>
        <tbody>
            <tr th:each="link : ${links.content}">
                <td><a th:href="@{/app/links/{id}(id=${link.id})}" th:text="${link.title ?: link.shortCode}">Title</a></td>
                <td th:text="'/' + ${link.shortCode}">/abc123</td>
                <td th:text="${link.clickCount}">0</td>
                <td th:text="${#temporals.format(link.createdAt, 'yyyy-MM-dd')}">2026-01-01</td>
            </tr>
        </tbody>
    </table>
</body>
</html>
```

- [ ] **Step 5: link-create.html 작성**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Linkly - 새 링크</title>
    <style>
        body { font-family: system-ui; max-width: 500px; margin: 50px auto; padding: 20px; }
        input, button { display: block; width: 100%; padding: 10px; margin: 10px 0; box-sizing: border-box; }
        button { background: #3b82f6; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 16px; }
    </style>
</head>
<body>
    <h1>새 링크 만들기</h1>
    <form th:action="@{/app/links}" method="post">
        <label>URL</label>
        <input type="url" name="url" placeholder="https://example.com" required>
        <label>제목 (선택)</label>
        <input type="text" name="title" placeholder="My Link">
        <button type="submit">단축 링크 생성</button>
    </form>
    <a th:href="@{/app/dashboard}">← 대시보드로 돌아가기</a>
</body>
</html>
```

- [ ] **Step 6: link-detail.html 작성**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Linkly - 링크 상세</title>
    <style>
        body { font-family: system-ui; max-width: 600px; margin: 50px auto; padding: 20px; }
        .stat { display: inline-block; padding: 20px; background: #f1f5f9; border-radius: 8px; margin: 5px; text-align: center; }
        .stat-value { font-size: 24px; font-weight: bold; }
        img { border: 1px solid #eee; border-radius: 8px; margin-top: 20px; }
    </style>
</head>
<body>
    <a th:href="@{/app/dashboard}">← 대시보드</a>
    <h1 th:text="${link.title ?: link.shortCode}">Link Title</h1>
    <p>원본: <a th:href="${link.originalUrl}" th:text="${link.originalUrl}" target="_blank">URL</a></p>
    <p>단축: <code th:text="'/' + ${link.shortCode}">/abc123</code></p>
    <div class="stat">
        <div class="stat-value" th:text="${link.clickCount}">0</div>
        <div>총 클릭</div>
    </div>
    <div>
        <h3>QR 코드</h3>
        <img th:src="@{/api/links/{id}/qr(id=${link.id})}" alt="QR Code" width="200">
    </div>
</body>
</html>
```

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/linkly/web/ src/main/resources/templates/
git commit -m "feat: add Thymeleaf web UI (login, dashboard, link create/detail)"
```

---

## Task 16: 전체 통합 테스트 + 최종 확인

- [ ] **Step 1: 전체 테스트 실행**

```bash
./gradlew test --info
```

Expected: All tests PASSED

- [ ] **Step 2: 애플리케이션 실행 확인 (Docker PostgreSQL 필요)**

```bash
docker run -d --name linkly-db -p 5432:5432 -e POSTGRES_DB=linkly -e POSTGRES_USER=linkly -e POSTGRES_PASSWORD=linkly postgres:16
./gradlew bootRun
```

Expected: 애플리케이션이 8080 포트에서 시작됨

- [ ] **Step 3: 수동 검증**

- `http://localhost:8080/app/login` 접속 → 로그인 페이지 표시
- 존재하지 않는 short code 접속 → 404
- OAuth2 설정이 완료되면 로그인 → 대시보드 → 링크 생성 → 리다이렉트 확인

- [ ] **Step 4: 최종 커밋**

```bash
git add -A
git commit -m "feat: Linkly URL Shortener v1.0 - complete implementation"
```
