# syntax=docker/dockerfile:1.7
# Multi-stage build for this reactor's one module, selected via SERVICE_MODULE:
#   docker build --build-arg SERVICE_MODULE=app -t pte-app .
#
# gateway-removal (follow-up to plans/modular-monolith Phase 11 cutover):
# `gateway` and `pte-common` are gone — `app` is the only module in the
# reactor now. SERVICE_MODULE stays as a build-arg (rather than hardcoding
# `app`) purely so this file doesn't need editing again if a second module
# ever shows up.

# No ARG in this stage — deliberately. An ARG here would make the stage's
# cache key differ per build invocation for no reason with a single-module
# reactor. Keeping the stage identical lets BuildKit run Maven once and reuse
# the result; the module is selected in the runtime stage instead.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -q package -DskipTests \
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
