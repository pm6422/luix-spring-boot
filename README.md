# luix-spring-boot

Lightweight, zero-bloat Java 25 and Spring Boot 4 toolkit shared across Luixtech services (`sky-trading-api`, `luix-scheduler`, etc.).

## Design Principles

- **Zero transitive bloat**: `luix-core` has **zero** runtime dependencies (pure JDK 25).
- **Opt-in starter**: `luix-spring-boot-starter` marks all Spring Security / Web / jOOQ / Logback integrations as `<optional>true</optional>` and guards every bean with `@ConditionalOnClass` + `@ConditionalOnMissingBean`.
- **No framework lock-in**: No `@Enable*` annotations, no forced `SecurityFilterChain`, no custom user table abstractions.

## Modules

### 1. `com.luixtech:luix-core` (Zero Runtime Dependencies)

| Package | Class | Purpose |
|---|---|---|
| `com.luixtech.core.id` | `TsidGenerator` | Thread-safe 64-bit numeric TSID generator (`2020-01-01Z` epoch, 42-bit ms + 10-bit node + 12-bit monotonic counter, bit-compatible with `tsid-creator`). |
| `com.luixtech.core.crypto` | `AesGcmCrypto` | AES-256-GCM authenticated encryption (SHA-256 key derivation, 12B IV, 128-bit tag), supporting `STANDARD_BASE64`, `URL_SAFE_BASE64`, and `decryptOrRaw(...)`. |
| `com.luixtech.core.ntfy` | `NtfyClient`, `NtfyMessage`, `NtfyAction` | Pure JDK `HttpClient` ntfy JSON API publisher with full UTF-8 / CJK / emoji support, Bearer auth, tags, icons, and view actions. |
| `com.luixtech.core.time` | `TimeZoneCatalog`, `TimeZoneOption` | Canonical IANA time zone catalog with formatted UTC offsets, zone validation, and locale normalization. |

### 2. `com.luixtech:luix-spring-boot-starter` (Spring Boot 4 Auto-Configuration)

| Feature | Classes | Config Prefix |
|---|---|---|
| **Logto / Passport OIDC** | `PassportOidcSupport`, `OidcUserClaims`, `PassportOidcAutoConfiguration`, `OidcAccount`, `OidcAccountStore`, `PassportOidcUserService`, `OidcUserSessionSyncFilter`, `CurrentOidcAccountResolver` | `luix.oidc.*` |
| **Admin User Safety Guard** | `AdminUserGuard`, `AdminUserGuardException` | Static utilities |
| **REST Error Handling** | `ApiError`, `LuixRestExceptionHandler` | Base `@RestControllerAdvice` |
| **Transaction Hook** | `TransactionAfterCommit` | Static utility |
| **Startup Summary Banner** | `StartupBannerLogger`, `StartupBannerAutoConfiguration` | `luix.banner.*` |
| **Bounded `RestClient` Timeouts** | `RestClientTimeoutAutoConfiguration` | `luix.http.*` |
| **jOOQ Helpers** | `JooqTypeSupport`, `JooqSortSupport` | Static utilities |

## Usage

```xml
<dependency>
    <groupId>com.luixtech</groupId>
    <artifactId>luix-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Or depend only on pure Java utilities without Spring:

```xml
<dependency>
    <groupId>com.luixtech</groupId>
    <artifactId>luix-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Configuration Properties (all optional)

```yaml
luix:
  banner:
    enabled: true
    template-location: config/banner-app.txt
  http:
    enabled: true
    connect-timeout: 10s
    read-timeout: 20s
  oidc:
    enabled: true
    id-token-jws-algorithm: ES384
    allow-at-jwt-type: true
```

## Local Build & Publish to Maven Central

### Local install (for testing in `sky-trading-api` / `luix-scheduler`)

```bash
./mvnw clean install -B
```

### Publish release to Sonatype Central Portal

1. Ensure `~/.m2/settings.xml` has server `sonatype-central` with your Central Portal User Token credentials and a GPG key configured.
2. Run:

```bash
./mvnw clean deploy -Prelease -Drevision=1.0.0 -B
```

Or push a git tag `v1.0.0` to trigger [.github/workflows/maven-publish.yml](.github/workflows/maven-publish.yml) (requires repo secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `MAVEN_GPG_PRIVATE_KEY`, `MAVEN_GPG_PASSPHRASE`).
