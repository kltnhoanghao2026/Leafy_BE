# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy parent pom and common module first
COPY pom.xml ./
COPY common/pom.xml ./common/
COPY common/src ./common/src

# Copy message-service module
COPY message-service/pom.xml ./message-service/
COPY message-service/src ./message-service/src

# Build the application
RUN mvn -f message-service/pom.xml clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built jar from build stage
COPY --from=build /app/message-service/target/*.jar app.jar

# Expose the port
EXPOSE 8092

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8092/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
