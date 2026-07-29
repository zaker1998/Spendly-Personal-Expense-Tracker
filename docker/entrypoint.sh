#!/bin/sh
set -e

# Convert Render/Railway style DATABASE_URL (postgres://...) to Spring JDBC settings
if [ -n "${DATABASE_URL:-}" ]; then
  # postgres://user:pass@host:port/db  OR  postgresql://...
  url_no_scheme=$(echo "$DATABASE_URL" | sed -E 's|^postgres(ql)?://||')
  userpass=$(echo "$url_no_scheme" | cut -d@ -f1)
  hostportdb=$(echo "$url_no_scheme" | cut -d@ -f2)
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-$(echo "$userpass" | cut -d: -f1)}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$(echo "$userpass" | cut -d: -f2-)}"
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://${hostportdb}}"
fi

# Render / platforms often inject PORT
if [ -n "${PORT:-}" ]; then
  export SERVER_PORT="$PORT"
fi

exec java -jar /app/app.jar
