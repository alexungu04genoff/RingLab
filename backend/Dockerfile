FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src/main ./src/main
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/quarkus-app/ ./
USER 10001:10001
ENV QUARKUS_PROFILE=prod
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-jar", "quarkus-run.jar"]
