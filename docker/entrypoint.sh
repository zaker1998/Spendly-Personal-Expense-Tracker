#!/bin/sh
set -e

# Render's own Postgres supplies DATABASE_URL (postgres://user:pass@host:port/db).
# An external database (Neon) is wired up with SPRING_DATASOURCE_* directly, so
# this block only runs as a fallback and never overrides values already set.
if [ -n "${DATABASE_URL:-}" ]; then
  url_no_scheme=${DATABASE_URL#postgres://}
  url_no_scheme=${url_no_scheme#postgresql://}

  # Split on the LAST '@': a generated password may legitimately contain one,
  # while the host part never does.
  userpass=${url_no_scheme%@*}
  hostportdb=${url_no_scheme##*@}

  user=${userpass%%:*}
  pass=${userpass#*:}

  # Credentials arrive percent-encoded in a URL; the JDBC driver wants them raw.
  urldecode() {
    printf '%b' "$(printf '%s' "$1" | sed 's/+/ /g; s/%\([0-9a-fA-F][0-9a-fA-F]\)/\\x\1/g')"
  }

  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-$(urldecode "$user")}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$(urldecode "$pass")}"
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://${hostportdb}}"
fi

if [ -n "${PORT:-}" ]; then
  export SERVER_PORT="$PORT"
fi

exec java -jar /app/app.jar
