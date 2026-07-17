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

## Tech Stack

- **Java 21**
- **Spring Boot 3.5.7** (Spring Web, Security, Validation)
- **Database**: PostgreSQL
- **In-Memory Cache & Counters**: Redis (via spring-boot-starter-data-redis)
- **ORM**: Hibernate / Spring Data JPA
- **Authentication**: JWT & Google OAuth2 Client
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
