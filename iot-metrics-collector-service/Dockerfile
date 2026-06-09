# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml ./
COPY iot-metrics-collector-service/pom.xml ./iot-metrics-collector-service/
COPY iot-metrics-collector-service/src ./iot-metrics-collector-service/src

RUN mvn -f iot-metrics-collector-service/pom.xml clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/iot-metrics-collector-service/target/*.jar app.jar

EXPOSE 8091

HEALTHCHECK --interval=20s --timeout=5s --start-period=45s --retries=10 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8091/iot/alert-events || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
