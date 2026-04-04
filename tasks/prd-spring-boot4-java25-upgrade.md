# PRD: Spring Boot 4.0.4 + Java 25 Upgrade

## Introduction

Upgrade the TobyShortUrl project from Spring Boot 3.5.0 / Java 17 to Spring Boot 4.0.4 / Java 25 to stay on the latest stable versions for security patches, performance improvements, and long-term support. This is a full migration that adopts new APIs, fixes deprecations, and updates all dependencies to compatible versions.

## Goals

- Upgrade Java from 17 to 25 (LTS)
- Upgrade Spring Boot from 3.5.0 to 4.0.4
- Upgrade Gradle to a version supporting Java 25
- Adopt new starter POM names (Boot 4 modularization)
- Fix all breaking changes and deprecation warnings
- Ensure all existing tests pass after migration

## User Stories

### US-001: Upgrade Gradle wrapper to support Java 25
**Description:** As a developer, I need Gradle to support Java 25 so the project compiles and runs correctly.

**Acceptance Criteria:**
- [ ] Gradle wrapper updated to latest version supporting Java 25
- [ ] `./gradlew build` runs without Gradle-level errors

### US-002: Upgrade Java toolchain from 17 to 25
**Description:** As a developer, I need the build to target Java 25 so we can use the latest JDK features and LTS support.

**Acceptance Criteria:**
- [ ] `build.gradle.kts` `languageVersion` changed from `17` to `25`
- [ ] All source code compiles under Java 25
- [ ] No `javax.*` imports remain (except `javax.crypto.*` which is standard JDK)

### US-003: Upgrade Spring Boot to 4.0.4 and update starter names
**Description:** As a developer, I need to update the Spring Boot plugin version and adopt the new modularized starter names introduced in Boot 4.

**Acceptance Criteria:**
- [ ] Spring Boot plugin version set to `4.0.4`
- [ ] `io.spring.dependency-management` plugin updated to compatible version
- [ ] Starter renames applied:
  - `spring-boot-starter-web` → `spring-boot-starter-webmvc`
  - `spring-boot-starter-oauth2-client` → `spring-boot-starter-security-oauth2-client`
- [ ] Flyway dependencies replaced with `spring-boot-starter-flyway` (remove standalone `flyway-core` and `flyway-database-postgresql`)
- [ ] `thymeleaf-extras-springsecurity6` updated to Boot 4 compatible version (springsecurity7 or equivalent)
- [ ] Test starter updated: add `spring-boot-starter-security-test` if needed
- [ ] Project compiles without dependency resolution errors

### US-004: Fix Security configuration for Spring Security 7
**Description:** As a developer, I need to update `SecurityConfig.java` to be compatible with Spring Security 7 shipped in Boot 4.

**Acceptance Criteria:**
- [ ] `SecurityConfig.java` compiles and works with Spring Security 7
- [ ] OAuth2 login flow still functions (Google + GitHub)
- [ ] CSRF configuration still applied
- [ ] Authorization rules (permitAll, authenticated) still enforced
- [ ] Typecheck passes

### US-005: Fix Jackson 3.x compatibility
**Description:** As a developer, I need to ensure Jackson 3.x (default in Boot 4) works with our serialization/deserialization.

**Acceptance Criteria:**
- [ ] No `com.fasterxml.jackson` imports remain (if any exist), or Jackson 2 compatibility module is added
- [ ] All API endpoints correctly serialize/deserialize JSON
- [ ] `@JsonComponent` updated to `@JacksonComponent` if used

### US-006: Update test annotations for Boot 4
**Description:** As a developer, I need to update test code to use Boot 4's testing conventions.

**Acceptance Criteria:**
- [ ] `@SpringBootTest` tests that use MockMvc have explicit `@AutoConfigureMockMvc`
- [ ] `@MockBean` / `@SpyBean` replaced with `@MockitoBean` / `@MockitoSpyBean` if used
- [ ] All 9 existing test classes compile and pass
- [ ] JaCoCo coverage report still generates successfully

### US-007: Update third-party dependencies to compatible versions
**Description:** As a developer, I need to update third-party libraries to versions compatible with Spring Boot 4 and Java 25.

**Acceptance Criteria:**
- [ ] `bucket4j-core` updated to Boot 4 / Jakarta EE 11 compatible version
- [ ] `caffeine` updated to compatible version
- [ ] `zxing` (core + javase) updated if needed
- [ ] `uap-java` updated if needed
- [ ] H2 test dependency updated if needed
- [ ] No runtime `ClassNotFoundException` or `NoSuchMethodError`

### US-008: Verify application starts and all tests pass
**Description:** As a developer, I need to confirm the upgrade is complete with no regressions.

**Acceptance Criteria:**
- [ ] `./gradlew clean build` succeeds
- [ ] All 9 test classes pass
- [ ] Application starts with `./gradlew bootRun` (connects to DB or fails gracefully on missing DB)
- [ ] No deprecation warnings from Spring Boot 4 APIs

## Functional Requirements

- FR-1: Update `build.gradle.kts` with Boot 4.0.4 plugin, new starter names, and Java 25 toolchain
- FR-2: Update `gradle/wrapper/gradle-wrapper.properties` to a Gradle version supporting Java 25
- FR-3: Update `SecurityConfig.java` for Spring Security 7 API compatibility
- FR-4: Update `RateLimitConfig.java` if servlet filter API changes in Servlet 6.1
- FR-5: Update all test classes for Boot 4 testing conventions (`@AutoConfigureMockMvc`, `@MockitoBean`)
- FR-6: Update `application.yml` if any property names changed (e.g., `spring.jackson.*`)
- FR-7: Update all third-party dependency versions to Boot 4 / Java 25 compatible versions
- FR-8: Fix any `HttpHeaders` usage if treated as `MultiValueMap` (Boot 4 breaking change)

## Non-Goals

- No adoption of new Boot 4 features (API versioning, resilience, RestTestClient, etc.)
- No migration from `RestTemplate` to `RestClient` (not currently used)
- No JSpecify null-safety annotation adoption
- No new tests — just make existing tests pass
- No Kotlin migration
- No GraalVM native image support

## Technical Considerations

- Project already uses `jakarta.*` namespace (no `javax.` Spring migration needed)
- `javax.crypto.*` in `IpHasher.java` is standard JDK — no changes needed
- Flyway migrations (SQL files) are unaffected — only the Flyway dependency/starter changes
- Hibernate will upgrade from 6.x to 7.x (JPA 3.2) — entity annotations should remain compatible
- Current Gradle version is 8.14.4 — may need upgrade for Java 25 support
- `thymeleaf-extras-springsecurity6` must be replaced with the Spring Security 7 equivalent

## Success Metrics

- `./gradlew clean build` passes with 0 test failures
- No Spring Boot deprecation warnings in build output
- Application context loads successfully in tests

## Open Questions

- Does `bucket4j-core:8.10.1` support Jakarta EE 11 / Servlet 6.1, or is a newer version needed?
- Is `thymeleaf-extras-springsecurity7` available, or has it been merged into a Boot 4 starter?
- Does the current Gradle 8.14.4 support Java 25, or do we need Gradle 9.x?
