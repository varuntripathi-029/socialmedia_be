# Social Media Backend API

This is the backend API for the Social Media application, built with Java 21 and Spring Boot.

## 🚀 Tech Stack

- **Java 21**
- **Spring Boot 3.5.7** (Web, Data JPA, Security, Validation)
- **PostgreSQL** (Database)
- **Hibernate / Spring Data JPA** (ORM)
- **JSON Web Tokens (JWT)** (Authentication)
- **Google API Client** (OAuth2 Authentication)
- **Lombok** (Boilerplate reduction)
- **Spring Dotenv** (Environment variable management)

## ✨ Features

- **Authentication & Authorization**: Email/Password and Google OAuth2 login via JWT.
- **User Management**: Profiles, avatars, and username history.
- **Social Graph**: Follow users, manage follow requests.
- **Posts**: Create posts, attach images, and search.
- **Events**: Create, RSVP, and participate in events. Includes location, date, college name, dress code, etc.
- **Event Rating & Reviews**: Rate events after they end and calculate host average ratings.
- **Engagement**: Comments and Likes on posts and events.
- **Notifications**: System notifications for likes, comments, follows, comments, and reviews.
- **Search**: Unified search endpoint for users, posts, and events.

## 🛠️ Setup & Local Development

### Prerequisites

- [Java 21](https://jdk.java.net/21/)
- [Maven](https://maven.apache.org/)
- [PostgreSQL](https://www.postgresql.org/)

### 1. Database Setup

Create a local PostgreSQL database. For example:

```sql
CREATE DATABASE socialmedia;
```

### 2. Environment Variables

Create a `.env` file in the root directory (`be/socialmedia/`) with the following variables:

```env
PORT=8080
DB_URL=jdbc:postgresql://localhost:5432/socialmedia
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password
JWT_SECRET=your_super_secret_jwt_key
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
FILE_UPLOAD_DIR=uploads
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
```

### 3. Build & Run

Run the application using Maven:

```bash
mvn clean install
mvn spring-boot:run
```

Alternatively, you can run the `SocialMediaApplication.java` main class directly from your IDE (IntelliJ IDEA, Eclipse, VS Code).

## 📡 API Endpoints Overview

- **Auth**: `/api/auth/**` (Login, Register, Google Auth, Check Username)
- **Users**: `/api/users/**` (Profiles, Avatars, Updates)
- **Posts**: `/api/posts/**` (Create, Retrieve, Feed)
- **Events**: `/api/events/**` (Create, RSVP, Update Status)
- **Event Reviews**: `/api/event-reviews/**` (Submit ratings/reviews for events)
- **Follows**: `/api/follows/**` (Follow, Unfollow, Accept/Reject requests)
- **Comments**: `/api/comments/**` (Add, Delete comments)
- **Likes**: `/api/likes/**` (Like, Unlike posts/events)
- **Notifications**: `/api/notifications/**` (Retrieve, Mark as read)
- **Search**: `/api/search/**` (Global search)

## 📁 File Uploads

Static files (images/avatars) are uploaded to the local directory defined by `FILE_UPLOAD_DIR` (default: `uploads/`).

## 🐳 Docker

### Build

```bash
docker build -t socialmedia-api .
```

### Run

```bash
docker run -p 8080:8080 --env-file .env socialmedia-api
```

Or pass environment variables individually:

```bash
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://localhost:5432/socialmedia \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=secret \
  -e JWT_SECRET=your-jwt-secret \
  -e GOOGLE_CLIENT_ID=your-id \
  -e GOOGLE_CLIENT_SECRET=your-secret \
  socialmedia-api
```

### Production Profile

Set `SPRING_PROFILES_ACTIVE=prod` to enable production settings (no verbose SQL, `validate` DDL, optimized connection pool).

## ☁️ Cloud Deployment (Render)

1. Connect this repo on [Render](https://render.com) → **New Web Service** → **Docker**
2. Set all required environment variables in the Render dashboard
3. Render auto-assigns `PORT` — the app picks it up via `server.port=${PORT:8080}`

> **Note:** For persistent file uploads, use Render's Persistent Disk or an external storage (S3, Cloudinary).
