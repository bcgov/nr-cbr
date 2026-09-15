#!/bin/sh
# Generate /srv/config.js from VITE_* env vars at container start.
#
# index.html loads /config.js before the app bundle; src/env.ts then merges window.config over
# import.meta.env, so these runtime values win. That is what lets ONE built image serve DEV/TEST/PROD
# and every PR preview, with the env-specific values supplied by the OpenShift Deployment rather than
# baked in at build time.
set -eu

# /tmp/coraza is created in the Dockerfile, but in Kubernetes it gets shadowed by the emptyDir mount
# on /tmp (needed for readOnlyRootFilesystem=true). Recreate it here so the Coraza WAF has its
# scratch dir.
mkdir -p /tmp/coraza

CONFIG_FILE=/srv/config.js

# JSON-escape for embedding in a double-quoted JS string. The values injected here are URLs, ids and
# short identifiers, so handling \ and " is sufficient.
escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

cat > "$CONFIG_FILE" <<CONFIG
// Generated at container start by docker-entrypoint.sh from VITE_* env vars.
window.config = {
  VITE_KEYCLOAK_URL: "$(escape "${VITE_KEYCLOAK_URL:-}")",
  VITE_KEYCLOAK_CLIENT_ID: "$(escape "${VITE_KEYCLOAK_CLIENT_ID:-}")",
  VITE_BACKEND_URL: "$(escape "${VITE_BACKEND_URL:-}")",
  VITE_BASE_PATH: "$(escape "${VITE_BASE_PATH:-}")",
  VITE_SUPPORT_EMAIL: "$(escape "${VITE_SUPPORT_EMAIL:-}")",
  VITE_ZONE: "$(escape "${VITE_ZONE:-dev}")"
};
CONFIG

exec /usr/bin/caddy "$@"
