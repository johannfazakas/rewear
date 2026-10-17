# syntax=docker/dockerfile:1

# The rewear-api image: fat jar on a JRE base, used for both dev and production
# (ADR-13 — the native image was dropped). Config comes entirely from env vars (ADR-9).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :app:buildFatJar

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
ARG GIT_SHA=unknown
ENV GIT_SHA=$GIT_SHA
COPY --from=build /workspace/app/build/libs/app-all.jar /app/rewear-api.jar
EXPOSE 8080
# Cap the heap to the container's memory limit so the JVM behaves on the ~4GB VPS (ADR-10).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/rewear-api.jar"]
