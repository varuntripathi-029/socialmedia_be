# ============================================
# Stage 1: Build the application with Maven
# ============================================
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Copy Maven wrapper and POM first (layer caching for dependencies)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ============================================
# Stage 2: Lightweight runtime image
# ============================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create uploads directory
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

# Copy only the built JAR from the builder stage
COPY --from=builder /build/target/socialmedia-0.0.1-SNAPSHOT.jar app.jar

# Switch to non-root user
USER appuser

# Expose default port
EXPOSE 8080

# Run the application
# PORT env var is picked up by Spring Boot via server.port=${PORT:8080}
ENTRYPOINT ["java", "-jar", "app.jar"]
