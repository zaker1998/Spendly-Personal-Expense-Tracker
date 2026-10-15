# syntax=docker/dockerfile:1

FROM node:26-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build -- --configuration=production

FROM maven:3.9.9-eclipse-temurin-21 AS backend-build
WORKDIR /app
COPY backend/pom.xml .
COPY backend/src ./src
COPY --from=frontend-build /frontend/dist/frontend/browser/ ./src/main/resources/static/
# Cache mount: see backend/Dockerfile for why this is not a go-offline layer.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spendly && adduser -S spendly -G spendly
COPY --from=backend-build /app/target/*.jar app.jar
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh && chown spendly:spendly /app/app.jar /entrypoint.sh
USER spendly
EXPOSE 8080
ENV SERVER_PORT=8080
# Render's free tier is 512 MB; the JVM's default max heap ignores the cgroup
# limit on containers this small and will happily get the process OOM-killed.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
ENTRYPOINT ["/entrypoint.sh"]
