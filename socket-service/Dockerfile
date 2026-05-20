# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy parent pom and common module first
COPY pom.xml ./
COPY common/pom.xml ./common/
COPY common/src ./common/src

# Copy socket-service module
COPY socket-service/pom.xml ./socket-service/
COPY socket-service/src ./socket-service/src

# Build the application
RUN mvn -f socket-service/pom.xml clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built jar from build stage
COPY --from=build /app/socket-service/target/*.jar app.jar

# Expose the port
EXPOSE 8093

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8093/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
