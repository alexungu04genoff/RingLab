#!/usr/bin/env bash
# Run through the manual workflow against an already initialized production stack.
set -euo pipefail

revision=${1:?Expected commit SHA}
backend_image=${2:?Expected backend digest reference}
frontend_image=${3:?Expected frontend digest reference}
[[ "$revision" =~ ^[0-9a-f]{40}$ ]]
[[ "$backend_image" =~ ^ghcr\.io/[a-z0-9._/-]+-backend@sha256:[0-9a-f]{64}$ ]]
[[ "$frontend_image" =~ ^ghcr\.io/[a-z0-9._/-]+-frontend@sha256:[0-9a-f]{64}$ ]]

cd /opt/ringlab
# Serialize against another manual invocation, including outside GitHub Actions.
exec 9>.deployment.lock
flock -n 9 || { echo 'Another production deployment is running.' >&2; exit 1; }
test -s production.env
test -s secrets/jwt/private.pem
test -s secrets/jwt/public.pem
test -s compose.production.yaml
umask 077

export BACKEND_IMAGE="$backend_image" FRONTEND_IMAGE="$frontend_image"
compose=(docker compose --env-file production.env -f compose.production.yaml)
"${compose[@]}" config --quiet
database_id=$("${compose[@]}" ps -q postgres)
test -n "$database_id" || { echo 'Initialize production PostgreSQL before using this workflow.' >&2; exit 1; }
test "$(docker inspect --format '{{.State.Health.Status}}' "$database_id")" = healthy

# Check/pull both digests before changing any running service. Private packages
# require root's VPS-side read-only GHCR login; runtime secrets never leave here.
"${compose[@]}" pull backend caddy

# This separate file contains image references only. Never rewrite production.env.
if test -f deployment.env; then
  cp deployment.env previous-deployment.env
fi
printf 'DEPLOYED_SHA=%s\nBACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n' \
  "$revision" "$backend_image" "$frontend_image" > deployment.env.pending
mv deployment.env.pending deployment.env
echo "Attempting production commit $revision"
echo "Backend: $backend_image"
echo "Frontend: $frontend_image"
# PostgreSQL is deliberately not recreated/upgraded by an application release.
"${compose[@]}" up -d --no-deps --pull never backend caddy

# Test the origin itself, not Cloudflare or the old development tunnel.
ready=false
for attempt in {1..24}; do
  if curl --noproxy '*' --fail --silent --show-error --max-time 10 \
      --resolve ringlabgarage.com:443:127.0.0.1 https://ringlabgarage.com/ >/dev/null &&
     curl --noproxy '*' --fail --silent --show-error --max-time 10 \
      --resolve ringlabgarage.com:443:127.0.0.1 https://ringlabgarage.com/api/racers >/dev/null; then
    ready=true
    break
  fi
  sleep 5
done
if [[ "$ready" != true ]]; then
  echo 'Origin checks failed. The selected images may be running; inspect the VPS. No automatic schema/image rollback was attempted.' >&2
  exit 1
fi
cp deployment.env last-successful-deployment.env
echo "Deployed production commit $revision; origin HTTPS checks passed."
