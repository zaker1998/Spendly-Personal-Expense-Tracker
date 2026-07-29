# syntax=docker/dockerfile:1
# Single image: Angular SPA + Spring Boot API (best for Render / Railway / Fly)

FROM node:22-alpine AS frontend-build
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
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spendly && adduser -S spendly -G spendly
COPY --from=backend-build /app/target/*.jar app.jar
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh && chown spendly:spendly /app/app.jar /entrypoint.sh
USER spendly
EXPOSE 8080
ENV SERVER_PORT=8080
ENTRYPOINT ["/entrypoint.sh"]
