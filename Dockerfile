# syntax=docker/dockerfile:1.7
# Shared multi-stage build for every module in this reactor (gateway + services/*).
# Which module gets built is selected purely via SERVICE_MODULE, e.g.:
#   docker build --build-arg SERVICE_MODULE=services/iam -t pte-iam .
#   docker build --build-arg SERVICE_MODULE=gateway -t pte-gateway .

FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE_MODULE
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -q -pl ${SERVICE_MODULE} -am package -DskipTests \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Daether.connector.http.retryHandler.count=5 \
    -Daether.connector.connectTimeout=30000 \
    -Daether.connector.requestTimeout=60000

FROM eclipse-temurin:21-jre AS runtime
ARG SERVICE_MODULE
# curl is required by this image's own docker-compose healthcheck (actuator/health has no other client in the base image).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/${SERVICE_MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
