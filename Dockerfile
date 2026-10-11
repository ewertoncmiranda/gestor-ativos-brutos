FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B clean verify


FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S app && adduser -S -G app -u 10001 app && mkdir -p /app/logs && chown -R app:app /app

COPY --from=build --chown=app:app /app/target/gestor-ativos-brutos-*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker

USER 10001:10001

ENTRYPOINT ["java", "-jar", "app.jar"]
