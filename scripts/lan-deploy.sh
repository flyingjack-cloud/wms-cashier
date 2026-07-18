#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

usage() {
    cat <<'EOF'
Usage: scripts/lan-deploy.sh <build|deploy|all> [env-file]

  build   Build the JAR and Docker image, then create an SCP-ready bundle.
  deploy  Upload an existing bundle and deploy it on the configured host.
  all     Build, upload, and deploy.

The env file defaults to .env.lan. Start from .env.lan.example.
EOF
}

die() {
    printf '[ERROR] %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

load_config() {
    local env_file=$1
    [[ -f "$env_file" ]] || die "Env file not found: $env_file"

    # This is a trusted, developer-owned shell env file. Quoting is supported.
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a

    # An explicitly empty value supports projects without Maven profiles.
    MAVEN_PROFILE="${MAVEN_PROFILE-dev}"
    MAVEN_SKIP_TESTS="${MAVEN_SKIP_TESTS:-true}"
    IMAGE_NAME="${IMAGE_NAME:-flyingjack-auth-service}"
    IMAGE_TAG="${IMAGE_TAG:-}"
    DOCKER_PLATFORM="${DOCKER_PLATFORM:-}"
    DOCKER_FILE_PATH="${DOCKER_FILE_PATH:-Dockerfile.lan}"
    CONTAINER_NAME="${CONTAINER_NAME:-auth-service}"
    HOST_PORT="${HOST_PORT:-9001}"
    CONTAINER_PORT="${CONTAINER_PORT:-9001}"
    MANAGEMENT_HOST_PORT="${MANAGEMENT_HOST_PORT:-8081}"
    MANAGEMENT_CONTAINER_PORT="${MANAGEMENT_CONTAINER_PORT:-8081}"
    RESTART_POLICY="${RESTART_POLICY:-unless-stopped}"
    DOCKER_NETWORK="${DOCKER_NETWORK:-}"
    HEALTHCHECK_URL="${HEALTHCHECK_URL:-http://127.0.0.1:${MANAGEMENT_HOST_PORT}/actuator/health}"
    HEALTHCHECK_RETRIES="${HEALTHCHECK_RETRIES:-30}"
    HEALTHCHECK_INTERVAL="${HEALTHCHECK_INTERVAL:-2}"
    DEPLOY_HOST="${DEPLOY_HOST:-}"
    DEPLOY_USER="${DEPLOY_USER:-}"
    DEPLOY_SSH_PORT="${DEPLOY_SSH_PORT:-22}"
    DEPLOY_REMOTE_DIR="${DEPLOY_REMOTE_DIR:-/tmp/auth-service-lan}"
    DEPLOY_KEEP_BUNDLE="${DEPLOY_KEEP_BUNDLE:-false}"
    BUNDLE_PATH="${BUNDLE_PATH:-}"
}

write_runtime_env() {
    local source_file=$1
    local destination=$2
    local line key value

    # Deployment/build settings stay outside the application container.
    : > "$destination"
    while IFS= read -r line || [[ -n "$line" ]]; do
        [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)= ]] || continue
        key=${BASH_REMATCH[1]}
        case "$key" in
            MAVEN_*|IMAGE_*|DOCKER_*|CONTAINER_*|HOST_PORT|MANAGEMENT_*|RESTART_POLICY|HEALTHCHECK_*|DEPLOY_*|BUNDLE_PATH)
                continue
                ;;
        esac
        [[ -v "$key" ]] || continue
        value=${!key}
        [[ "$value" != *$'\n'* && "$value" != *$'\r'* ]] || \
            die "Runtime variable $key must be a single-line value."
        printf '%s=%s\n' "$key" "$value" >> "$destination"
    done < "$source_file"
    chmod 600 "$destination"
}

write_deploy_config() {
    local destination=$1
    local key
    local keys=(
        IMAGE_NAME IMAGE_TAG CONTAINER_NAME HOST_PORT CONTAINER_PORT
        MANAGEMENT_HOST_PORT MANAGEMENT_CONTAINER_PORT RESTART_POLICY DOCKER_NETWORK
        HEALTHCHECK_URL HEALTHCHECK_RETRIES HEALTHCHECK_INTERVAL
    )

    : > "$destination"
    for key in "${keys[@]}"; do
        printf '%s=%q\n' "$key" "${!key}" >> "$destination"
    done
}

build_bundle() {
    local env_file=$1
    local version image_ref bundle_name bundle_path staging_dir
    local -a mvn_args docker_args

    require_command mvn
    require_command docker
    require_command tar

    if [[ -z "$IMAGE_TAG" ]]; then
        version="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
        [[ -n "$version" ]] || die 'Unable to read project.version from Maven.'
        IMAGE_TAG="${version}${MAVEN_PROFILE:+-$MAVEN_PROFILE}"
    fi
    image_ref="${IMAGE_NAME}:${IMAGE_TAG}"

    mvn_args=(clean package)
    if [[ -n "$MAVEN_PROFILE" ]]; then
        mvn_args+=(-P "$MAVEN_PROFILE")
    fi
    if [[ "$MAVEN_SKIP_TESTS" == "true" ]]; then
        mvn_args+=(-DskipTests)
    fi

    printf '[INFO] Building Maven artifact%s...\n' "${MAVEN_PROFILE:+ with profile $MAVEN_PROFILE}"
    (cd "$PROJECT_DIR" && mvn "${mvn_args[@]}")

    [[ -f "$PROJECT_DIR/$DOCKER_FILE_PATH" ]] || die "Dockerfile not found: $DOCKER_FILE_PATH"
    docker_args=(build -f "$PROJECT_DIR/$DOCKER_FILE_PATH" -t "$image_ref")
    if [[ -n "$DOCKER_PLATFORM" ]]; then
        docker_args+=(--platform "$DOCKER_PLATFORM")
    fi
    docker_args+=("$PROJECT_DIR")

    printf '[INFO] Building Docker image %s...\n' "$image_ref"
    docker "${docker_args[@]}"

    bundle_name="${IMAGE_NAME//\//_}-${IMAGE_TAG}.tar.gz"
    bundle_path="$PROJECT_DIR/dist/lan/$bundle_name"
    staging_dir="$(mktemp -d)"
    trap 'rm -rf "$staging_dir"' RETURN

    printf '[INFO] Exporting Docker image...\n'
    docker save -o "$staging_dir/image.tar" "$image_ref"
    cp "$SCRIPT_DIR/lan-remote-deploy.sh" "$staging_dir/deploy.sh"
    chmod 700 "$staging_dir/deploy.sh"
    write_runtime_env "$env_file" "$staging_dir/app.env"
    write_deploy_config "$staging_dir/deploy.conf"

    mkdir -p "$(dirname "$bundle_path")"
    tar -C "$staging_dir" -czf "$bundle_path" image.tar deploy.sh deploy.conf app.env
    chmod 600 "$bundle_path"
    printf '%s\n' "$bundle_path" > "$PROJECT_DIR/dist/lan/.latest"
    rm -rf "$staging_dir"
    trap - RETURN

    printf '[SUCCESS] Bundle created: %s\n' "$bundle_path"
    printf '[INFO] Bundle size: %s\n' "$(du -h "$bundle_path" | awk '{print $1}')"
}

find_bundle() {
    local latest_file="$PROJECT_DIR/dist/lan/.latest"
    local -a bundles

    if [[ -n "$BUNDLE_PATH" ]]; then
        [[ "$BUNDLE_PATH" = /* ]] || BUNDLE_PATH="$PROJECT_DIR/$BUNDLE_PATH"
    elif [[ -f "$latest_file" ]]; then
        BUNDLE_PATH="$(<"$latest_file")"
    else
        shopt -s nullglob
        bundles=("$PROJECT_DIR"/dist/lan/*.tar.gz)
        shopt -u nullglob
        ((${#bundles[@]} > 0)) || die 'No bundle found. Run the build command first.'
        BUNDLE_PATH="${bundles[${#bundles[@]} - 1]}"
    fi
    [[ -f "$BUNDLE_PATH" ]] || die "Bundle not found: $BUNDLE_PATH"
}

deploy_bundle() {
    local remote_target remote_bundle remote_command
    local -a ssh_args scp_args

    require_command ssh
    require_command scp
    [[ -n "$DEPLOY_HOST" ]] || die 'DEPLOY_HOST must be set for deploy.'
    [[ "$DEPLOY_REMOTE_DIR" =~ ^/[A-Za-z0-9._/-]+$ ]] || \
        die 'DEPLOY_REMOTE_DIR must be an absolute path without spaces.'
    find_bundle

    remote_target="$DEPLOY_HOST"
    [[ -z "$DEPLOY_USER" ]] || remote_target="${DEPLOY_USER}@${DEPLOY_HOST}"
    remote_bundle="${DEPLOY_REMOTE_DIR}/$(basename "$BUNDLE_PATH")"
    ssh_args=(-p "$DEPLOY_SSH_PORT")
    scp_args=(-P "$DEPLOY_SSH_PORT")

    printf '[INFO] Uploading bundle to %s:%s...\n' "$remote_target" "$DEPLOY_REMOTE_DIR"
    ssh "${ssh_args[@]}" "$remote_target" "mkdir -p '$DEPLOY_REMOTE_DIR' && chmod 700 '$DEPLOY_REMOTE_DIR'"
    scp "${scp_args[@]}" "$BUNDLE_PATH" "${remote_target}:${remote_bundle}"

    printf -v remote_command \
        "tar -xzf %q -C %q && chmod 700 %q/deploy.sh && %q/deploy.sh %q %q" \
        "$remote_bundle" "$DEPLOY_REMOTE_DIR" "$DEPLOY_REMOTE_DIR" \
        "$DEPLOY_REMOTE_DIR" "$DEPLOY_REMOTE_DIR" "$remote_bundle"
    if [[ "$DEPLOY_KEEP_BUNDLE" == "true" ]]; then
        remote_command+=" --keep-bundle"
    fi

    printf '[INFO] Starting remote deployment...\n'
    ssh "${ssh_args[@]}" "$remote_target" "$remote_command"
}

main() {
    local command=${1:-}
    local env_file=${2:-"$PROJECT_DIR/.env.lan"}

    case "$command" in
        build|deploy|all) ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; exit 2 ;;
    esac

    [[ "$env_file" = /* ]] || env_file="$PROJECT_DIR/$env_file"
    load_config "$env_file"

    case "$command" in
        build) build_bundle "$env_file" ;;
        deploy) deploy_bundle ;;
        all)
            build_bundle "$env_file"
            BUNDLE_PATH="$(<"$PROJECT_DIR/dist/lan/.latest")"
            deploy_bundle
            ;;
    esac
}

main "$@"
