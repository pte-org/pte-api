# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -DskipTests

COPY src/ src/

RUN ./mvnw clean package -DskipTests


FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

RUN groupadd --system aptis \
    && useradd --system --gid aptis aptis

COPY --from=builder /workspace/target/*.jar app.jar

USER aptis

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]