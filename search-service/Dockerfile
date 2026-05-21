FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache libc6-compat
ARG MODULE_NAME
COPY ${MODULE_NAME}/target/*.jar app.jar
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app.jar"]
