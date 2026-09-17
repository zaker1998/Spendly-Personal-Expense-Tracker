# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# A cache mount rather than a `dependency:go-offline` layer: go-offline misses
# artifacts resolved late in the build (jboss-logging, for one), so an offline
# package step fails. The mount keeps ~/.m2 between builds without that trap,
# and a source edit still never re-downloads dependencies.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# curl is the healthcheck; it is the only thing added to the runtime image.
RUN apk add --no-cache curl \
    && addgroup -S spendly && adduser -S spendly -G spendly
COPY --from=build /app/target/*.jar app.jar
RUN chown spendly:spendly /app/app.jar
USER spendly
EXPOSE 8080
# The container, not the host, is the memory budget: the JVM's default heap is a
# fraction of total RAM and ignores the cgroup limit on small instances.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
