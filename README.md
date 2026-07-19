# Social Media Backend API

This repository houses the backend API for the Social Media platform, built with Java 21, Spring Boot 3, and PostgreSQL. It manages core resources including authentication, social graphs, posts, notifications, and events.

## Engineering Challenges and Solutions

Building a scalable, production-grade social platform required addressing several database integrity, rate limiting, and caching challenges. Below is an outline of the technical issues faced during backend implementation and the engineering solutions applied.

### N+1 Query Amplification and PostgreSQL Connection Saturation
Listing events via the primary event feed endpoint required joining the organizer's user records, counting event participation states, and sorting them. Under moderate user traffic, these heavy SQL read transactions repeatedly saturated the database connection pool, leading to thread starvation and latency spikes.
* **Solution**: Implemented a Redis cache-aside strategy for the events feed. The JSON-serialized EventResponse data is cached under the key `events:feed:all` with a 5-minute TTL. The backend checks the in-memory cache first, falling back to PostgreSQL only on a cache miss. The cache is proactively invalidated on event creation, ending, deletion, or participation state changes. This reduced read database transactions by up to 95%.

### Concurrent RSVP Inserts and Duplicate Participation Records
Simultaneous join requests from the same user to the same event led to race conditions. Under concurrent HTTP requests, database duplicate checks in Spring Boot's service layer would run simultaneously, leading both transactions to save new rows and violating logical constraints.
* **Solution**: Enforced a database-level unique constraint across user ID and event ID combinations on the `event_participants` table. During execution, the join operation catches the thrown `DataIntegrityViolationException` and throws a clean `IllegalStateException` mapped to an HTTP 400 Bad Request, preventing database corruption under concurrent loads.

### API Brute Force Vulnerability and SQL Logging Contention
Standard rate-limiting strategies that log client requests to a database table introduced huge write amplification. Every single API hit forced a database insert, and checking historical request windows spiked table locks and latency.
* **Solution**: Developed a high-performance, atomic rate-limiting system using Redis counters. Implemented a fixed-window algorithm utilizing Redis `INCR` and `EXPIRE` commands in a custom OncePerRequestFilter. To prevent database user lookups on every single rate-limited request, we cache username-to-userId mappings in Redis for 1 hour, allowing the filter to perform rate-limit validation in less than 3 milliseconds without hitting PostgreSQL.

### High Availability and Single Point of Failure (SPOF)
Relying entirely on a caching layer introduces a Single Point of Failure. If the Redis server experiences network dropouts, memory exhaustion, or restarts, throwing exceptions during cache or rate-limiting lookups would crash all core APIs.
* **Solution**: Integrated a resilient failover mechanism. Every Redis template call in the cache-aside and trending events system is wrapped in localized try-catch blocks. If a connection exception is caught, the failure is logged as a warning, and the API gracefully falls back to performing standard PostgreSQL queries, ensuring the application remains fully functional when Redis is offline.

### Ephemeral Filesystem Wiping User Media on Every Redeploy
User-uploaded post and profile images were written directly to local disk under `uploads/` and served back via a static `/uploads/**` mapping. This works fine on a persistent server, but PaaS platforms (first Railway, now Render) run the app inside a container filesystem that is rebuilt from scratch on every deploy. PostgreSQL data survived because it lives in an external managed database (Neon), but every image on disk was silently lost on each redeploy.
* **Solution**: Migrated media storage behind a `FileStorageProvider` interface (strategy pattern), with `CloudinaryStorageProvider` as the active implementation, selected via the `storage.provider` property. `FileStorageService` no longer knows or cares which backend is behind it — it just delegates to whichever provider is wired in. The uploaded file is streamed straight to Cloudinary and the returned `secure_url` (a permanent CDN link) is persisted on the entity instead of a local file path, so storage is fully decoupled from the application container and survives redeploys, restarts, and horizontal scaling. Adding S3 or another backend later is a single new class implementing `FileStorageProvider` — no existing code changes.

### Redis Command Hang on Partial Outage
The Redis fail-open design above only helps once a call actually throws — but without an explicit command timeout, Lettuce's client defaults to a 60-second timeout per command. A degraded-but-not-dead Redis instance could therefore block every rate-limited or cached request for up to a minute before falling back, rather than failing fast.
* **Solution**: Set `spring.data.redis.timeout=300ms` explicitly, bounding the worst-case latency of any single Redis call so the existing fail-open fallback engages quickly instead of hanging the request thread.

### Unbounded Feed, Search, and Notification Queries Degrading as Data Grows
The posts feed, combined search, and notification endpoints returned every matching row with no limit at all. This doesn't break on day one, but every one of those queries gets slower as the underlying tables grow, which quietly shrinks how many requests the connection pool can turn over per second even though the pool size itself never changes.
* **Solution**: Added `page`/`size` query parameters (default 20, clamped to a max of 50 via a shared `Constants.clampPageSize` helper) to `GET /api/posts`, `/posts/feed`, `/posts/search`, `/api/search`, and `/api/notifications(/unread)`. The response shape is unchanged — still a flat JSON array — so the frontend required zero changes; the bound alone keeps per-query cost constant regardless of how large the tables get.

### Extending Cache-Aside Beyond the Events Feed Without Leaking Per-User State
The events-feed cache-aside pattern worked well, but copying it directly to the posts feed would have introduced a real correctness bug: `PostResponse` includes `isLikedByCurrentUser`, a field that's different for every viewer. Caching the fully-built response under one shared key would have leaked one user's like status to every other user reading that same cache entry.
* **Solution**: Split the cached payload from the per-viewer overlay. The posts feed cache stores only what's actually shared across viewers (everything except like state) with a short 60-second TTL and no active eviction — post creation is far too frequent for whole-cache invalidation to be worth it, unlike events. `isLikedByCurrentUser` is then computed fresh on every request via one batched query against the returned post IDs, whether the rest of the response came from cache or not. Notifications and user profiles needed different shapes for the same underlying reason: notifications are inherently per-user, so they're cached under a per-user key and evicted precisely on every create/read/delete rather than relying on a TTL; profiles carry no per-viewer fields at all, so they cache safely at a global key with a longer 10-minute TTL, evicted on `updateProfile`. Redis connection settings (host, port, credentials, TLS) were also made fully environment-driven so a hosted Redis instance can back all of this in production instead of only the local docker-compose one.

### Rate Limiter Trusting a Client-Controlled Header
The IP-based rate limit rules (login, signup) keyed off the first entry in the `X-Forwarded-For` header — but that header is fully attacker-controlled unless a trusted proxy is verified first. A client sending its own `X-Forwarded-For` value on every request could get a fresh Redis key each time, bypassing the 5-requests-per-minute throttle entirely.
* **Solution**: `RateLimitFilter` now trusts the *last* entry in the `X-Forwarded-For` chain instead of the first. Each proxy hop appends the IP it observed to the right side of the header, so the rightmost entry is whatever the app's one trusted reverse proxy (the hosting platform's edge) actually saw — a value the client cannot forge, unlike the leftmost entry it fully controls.

### Private Account Follow Lists Bypassing the Privacy Model
The profile endpoint correctly hides a private account's followers/following lists from anyone who isn't the owner or an accepted follower — but `GET /api/follows/{userId}/followers` and `/following` were separate endpoints that skipped this check entirely, returning the full list (plus each user's email) to any authenticated caller for any account, private or not.
* **Solution**: Applied the same visibility rule used by the profile endpoint directly in `FollowController` — a private account's follow lists now return empty to anyone who isn't the owner or an accepted follower, closing the gap between two endpoints that were supposed to enforce the same privacy model.

### Email Addresses Leaking Through Every Shared User DTO
`UserResponse`, used to embed "who posted/commented/organized this" across nearly every endpoint, included a raw `email` field populated unconditionally — meaning post authors, comment authors, event organizers and reviewers, search results, and follow lists all leaked real email addresses to any authenticated viewer, not just the account owner.
* **Solution**: Removed `email` from every "other user" context across the codebase, keeping it only where a user is legitimately looking at their own account — login, registration, Google OAuth sign-in, and `GET /api/users/me`. Every other call site (profile views of other users, search, posts, comments, events, reviews, follow lists) now omits it.

## Tech Stack

- **Java 21**
- **Spring Boot 3.5.7** (Spring Web, Security, Validation)
- **Database**: PostgreSQL
- **In-Memory Cache & Counters**: Redis (via spring-boot-starter-data-redis)
- **ORM**: Hibernate / Spring Data JPA
- **Authentication**: JWT & Google OAuth2 Client
- **Media Storage**: Cloudinary
- **Boilerplate Reduction**: Lombok

## Setup and Local Development

### Prerequisites

- Java 21 JDK
- Maven
- Docker and Docker Compose

### 1. Run Supporting Services
Start local PostgreSQL and Redis containers using the provided Docker Compose file:

```bash
docker-compose up -d
```

### 2. Build and Run the Application
Compile the source code and boot the Spring application:

```bash
mvn clean compile
mvn spring-boot:run
```

## API Rate Limiting Rules

- **POST /api/auth/login**: 5 requests/minute per IP
- **POST /api/auth/signup**: 5 requests/minute per IP
- **POST /api/posts**: 10 requests/hour per user
- **POST /api/events**: 5 requests/day per user
- **POST /api/events/{id}/join**: 20 requests/hour per user
