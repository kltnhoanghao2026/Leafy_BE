# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml ./
COPY iot-test-data-service/pom.xml ./iot-test-data-service/
COPY iot-test-data-service/src ./iot-test-data-service/src

RUN mvn -f iot-test-data-service/pom.xml clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/iot-test-data-service/target/*.jar app.jar

EXPOSE 8099

HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=10 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8099/seed/simulation/status || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
