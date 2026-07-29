#!/bin/sh
set -e

# Map DATABASE_URL (postgres://user:pass@host:port/db) to Spring datasource env vars
if [ -n "${DATABASE_URL:-}" ]; then
  url_no_scheme=$(echo "$DATABASE_URL" | sed -E 's|^postgres(ql)?://||')
  userpass=$(echo "$url_no_scheme" | cut -d@ -f1)
  hostportdb=$(echo "$url_no_scheme" | cut -d@ -f2)
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-$(echo "$userpass" | cut -d: -f1)}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$(echo "$userpass" | cut -d: -f2-)}"
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://${hostportdb}}"
fi

if [ -n "${PORT:-}" ]; then
  export SERVER_PORT="$PORT"
fi

exec java -jar /app/app.jar
