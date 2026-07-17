#!/usr/bin/env bash

set -Eeuo pipefail

die() {
    printf '[ERROR] %s\n' "$*" >&2
    exit 1
}

DEPLOY_DIR=${1:-}
BUNDLE_PATH=${2:-}
KEEP_BUNDLE=${3:-}

[[ -n "$DEPLOY_DIR" ]] || die 'Deployment directory is required.'
[[ -f "$DEPLOY_DIR/deploy.conf" ]] || die 'deploy.conf is missing.'
[[ -f "$DEPLOY_DIR/app.env" ]] || die 'app.env is missing.'
[[ -f "$DEPLOY_DIR/image.tar" ]] || die 'image.tar is missing.'
command -v docker >/dev/null 2>&1 || die 'Docker is not installed on the target host.'

# shellcheck disable=SC1091
source "$DEPLOY_DIR/deploy.conf"
IMAGE_REF="${IMAGE_NAME}:${IMAGE_TAG}"

printf '[INFO] Loading Docker image %s...\n' "$IMAGE_REF"
docker load -i "$DEPLOY_DIR/image.tar"
docker image inspect "$IMAGE_REF" >/dev/null 2>&1 || die "Loaded image not found: $IMAGE_REF"

if [[ -n "$DOCKER_NETWORK" ]] && ! docker network inspect "$DOCKER_NETWORK" >/dev/null 2>&1; then
    printf '[INFO] Creating Docker network %s...\n' "$DOCKER_NETWORK"
    docker network create "$DOCKER_NETWORK" >/dev/null
fi

if docker container inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
    printf '[INFO] Replacing existing container %s...\n' "$CONTAINER_NAME"
    docker rm -f "$CONTAINER_NAME" >/dev/null
fi

docker_args=(
    run -d
    --name "$CONTAINER_NAME"
    --restart "$RESTART_POLICY"
    --env-file "$DEPLOY_DIR/app.env"
    -p "${HOST_PORT}:${CONTAINER_PORT}"
)
if [[ -n "$MANAGEMENT_HOST_PORT" ]]; then
    docker_args+=(-p "${MANAGEMENT_HOST_PORT}:${MANAGEMENT_CONTAINER_PORT}")
fi
if [[ -n "$DOCKER_NETWORK" ]]; then
    docker_args+=(--network "$DOCKER_NETWORK")
fi
docker_args+=("$IMAGE_REF")

printf '[INFO] Starting container %s...\n' "$CONTAINER_NAME"
docker "${docker_args[@]}" >/dev/null

if [[ -n "$HEALTHCHECK_URL" ]]; then
    command -v curl >/dev/null 2>&1 || die 'curl is required when HEALTHCHECK_URL is set.'
    printf '[INFO] Waiting for health endpoint: %s\n' "$HEALTHCHECK_URL"
    healthy=false
    for ((attempt = 1; attempt <= HEALTHCHECK_RETRIES; attempt++)); do
        if curl --fail --silent --max-time 2 "$HEALTHCHECK_URL" >/dev/null; then
            healthy=true
            break
        fi
        if ! docker container inspect "$CONTAINER_NAME" --format '{{.State.Running}}' 2>/dev/null | grep -q true; then
            docker logs --tail 100 "$CONTAINER_NAME" >&2 || true
            die 'Container exited before becoming healthy.'
        fi
        sleep "$HEALTHCHECK_INTERVAL"
    done
    if [[ "$healthy" != "true" ]]; then
        docker logs --tail 100 "$CONTAINER_NAME" >&2 || true
        die "Health check timed out after ${HEALTHCHECK_RETRIES} attempts."
    fi
fi

chmod 600 "$DEPLOY_DIR/app.env" "$DEPLOY_DIR/deploy.conf"
if [[ "$KEEP_BUNDLE" != "--keep-bundle" && -n "$BUNDLE_PATH" ]]; then
    rm -f "$BUNDLE_PATH"
fi

printf '[SUCCESS] %s is running from image %s.\n' "$CONTAINER_NAME" "$IMAGE_REF"
docker ps --filter "name=^/${CONTAINER_NAME}$" --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
