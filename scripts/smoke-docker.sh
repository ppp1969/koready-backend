#!/usr/bin/env bash

set -Eeuo pipefail

readonly image="${1:-koready-backend:local}"
readonly repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly project_name="${KOREADY_SMOKE_PROJECT:-koready-smoke-$$}"
readonly app_port="${KOREADY_SMOKE_APP_PORT:-18080}"
readonly db_port="${KOREADY_SMOKE_DB_PORT:-13306}"
readonly timeout_seconds="${KOREADY_SMOKE_TIMEOUT_SECONDS:-180}"
readonly expected_memory_bytes=$((512 * 1024 * 1024))

export APP_IMAGE="$image"
export APP_PORT="$app_port"
export DB_PORT="$db_port"
export DB_DATABASE="koready_smoke"
export DB_USERNAME="koready_smoke"
export DB_PASSWORD="koready-smoke-local"
export DB_ROOT_PASSWORD="koready-root-smoke-local"

compose=(docker compose -f "$repository_root/compose.yml" -p "$project_name")

cleanup() {
    local status=$?
    trap - EXIT
    set +e
    if ((status != 0)); then
        echo "Docker smoke test failed. Recent application logs:" >&2
        "${compose[@]}" --profile full logs --no-color --tail 200 app >&2
    fi
    "${compose[@]}" --profile full down --volumes --remove-orphans >/dev/null 2>&1
    exit "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

docker image inspect "$image" >/dev/null
"${compose[@]}" --profile full up -d --no-build

deadline=$((SECONDS + timeout_seconds))
response=""
while ((SECONDS < deadline)); do
    app_id="$("${compose[@]}" --profile full ps -q app)"
    if [[ -n "$app_id" ]]; then
        running="$(docker inspect --format '{{.State.Running}}' "$app_id")"
        if [[ "$running" != "true" ]]; then
            echo "Application container stopped before readiness." >&2
            exit 1
        fi
    fi

    if response="$(curl --fail --silent --show-error --max-time 5 \
        "http://127.0.0.1:${app_port}/actuator/health/readiness" 2>/dev/null)" \
        && grep -q '"status":"UP"' <<<"$response"; then
        break
    fi
    sleep 2
done

if ! grep -q '"status":"UP"' <<<"$response"; then
    echo "Application readiness did not become UP within ${timeout_seconds}s." >&2
    exit 1
fi

app_id="$("${compose[@]}" --profile full ps -q app)"
memory_limit="$(docker inspect --format '{{.HostConfig.Memory}}' "$app_id")"
if [[ "$memory_limit" != "$expected_memory_bytes" ]]; then
    echo "Expected a 512MiB memory limit, got ${memory_limit} bytes." >&2
    exit 1
fi

oom_killed="$(docker inspect --format '{{.State.OOMKilled}}' "$app_id")"
if [[ "$oom_killed" != "false" ]]; then
    echo "Application container was OOM-killed." >&2
    exit 1
fi

memory_usage="$(docker stats --no-stream --format '{{.MemUsage}}' "$app_id")"
echo "Docker smoke passed: readiness=UP, memoryLimit=512MiB, oomKilled=false, usage=${memory_usage}."
