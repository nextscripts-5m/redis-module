# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package \
    && cp target/sentinel-replication-lab.jar target/application.jar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/target/application.jar /app/application.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
