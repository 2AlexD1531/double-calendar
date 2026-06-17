# Этап сборки
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Этап запуска
FROM eclipse-temurin:21-jre-alpine
RUN apk update && \
    apk add --no-cache curl
WORKDIR /app
COPY --from=build /app/target/double-calendar-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

