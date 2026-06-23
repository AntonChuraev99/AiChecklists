#!/usr/bin/env bash
# Cloudflare Workers Builds — build script for Gisti wasmJs target.
#
# Called from the Dashboard "Build command" field as: bash .cloudflare/build.sh
# Default deploy command stays `npx wrangler deploy`, which reads `wrangler.jsonc`
# and uploads `composeApp/build/dist/wasmJs/productionExecutable` as static assets.
#
# Environment: Ubuntu, Node 22 preinstalled, JDK NOT preinstalled.
# Limits: 20 min build timeout, 8 GB RAM, 20 GB disk.
#   See: https://developers.cloudflare.com/workers/ci-cd/builds/limits-and-pricing/
#
# Caching note:
#   Workers Builds cache only preserves built-in paths (.npm, .cache/yarn, etc).
#   Custom paths NOT supported as of 2026-05.
#   See: https://developers.cloudflare.com/workers/ci-cd/builds/build-caching/
#   That means JDK + Gradle user home are re-downloaded on every CI run.
#
# JVM memory: Gradle launcher 6 GiB, Kotlin daemon 4 GiB.
# wasmJs link step is OOM-prone — keep these tuned.

set -euo pipefail

CACHE_DIR="$PWD/.ci-cache"
mkdir -p "$CACHE_DIR"

JDK_VERSION="17.0.13+11"
JDK_ENCODED="17.0.13%2B11"
JDK_TARBALL="OpenJDK17U-jdk_x64_linux_hotspot_17.0.13_11.tar.gz"
JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${JDK_ENCODED}/${JDK_TARBALL}"
JDK_HOME="$CACHE_DIR/jdk"

echo "::group::Setup JDK 17 (Temurin)"
if [ -x "$JDK_HOME/bin/java" ]; then
	echo "JDK already cached at $JDK_HOME"
else
	echo "Downloading JDK $JDK_VERSION..."
	mkdir -p "$JDK_HOME"
	curl -fsSL "$JDK_URL" -o /tmp/jdk.tar.gz
	tar -xzf /tmp/jdk.tar.gz -C "$JDK_HOME" --strip-components=1
	rm -f /tmp/jdk.tar.gz
fi
export JAVA_HOME="$JDK_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
echo "::endgroup::"

echo "::group::Resolve deploy environment"
# WORKERS_CI_BRANCH injected by Workers Builds since 2025-06.
# https://developers.cloudflare.com/workers/ci-cd/builds/configuration/#environment-variables
BRANCH="${WORKERS_CI_BRANCH:-unknown}"
if [ "$BRANCH" = "master" ]; then
	DEPLOY_ENV="production"
else
	DEPLOY_ENV="preview"
fi
echo "Branch: $BRANCH"
echo "Deploy env: $DEPLOY_ENV"
echo "::endgroup::"

echo "::group::Materialize local.properties from CI secrets"
# Cloudflare Builds → Settings → Variables and Secrets supplies these as env.
# generateWasmInitJs Gradle task reads `local.properties`.
# Project-level non-secret values (project ID, sender ID) hardcoded below —
# they're already public (visible in every Firebase URL and APK).
cat > local.properties <<EOF
sdk.dir=/dev/null
FIREBASE_WEB_API_KEY=${FIREBASE_WEB_API_KEY:-}
FIREBASE_WEB_APP_ID=${FIREBASE_WEB_APP_ID:-}
AMPLITUDE_KEY=${AMPLITUDE_KEY:-}
FIREBASE_WEB_PROJECT_ID=aichecklists-40230
FIREBASE_WEB_AUTH_DOMAIN=aichecklists-40230.firebaseapp.com
FIREBASE_WEB_STORAGE_BUCKET=aichecklists-40230.firebasestorage.app
FIREBASE_WEB_MESSAGING_SENDER_ID=27698629989
EOF
log_secret() {
	local name="$1"
	local value="${!name:-}"
	if [ -n "$value" ]; then
		echo "  $name: set"
	else
		echo "  $name: NOT SET — init.js will use stub for this key"
	fi
}
log_secret FIREBASE_WEB_API_KEY
log_secret FIREBASE_WEB_APP_ID
log_secret AMPLITUDE_KEY
echo "::endgroup::"

echo "::group::Gradle :composeApp:wasmJsBrowserDistribution"
export GRADLE_USER_HOME="$CACHE_DIR/gradle"
export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Xmx6g -XX:MaxMetaspaceSize=1g -XX:+UseParallelGC"
KOTLIN_DAEMON_ARGS="-Xmx4g,-XX:MaxMetaspaceSize=512m,-XX:+UseParallelGC"
chmod +x ./gradlew
./gradlew --version
./gradlew composeApp:wasmJsBrowserDistribution \
	--no-daemon --stacktrace --build-cache \
	"-Dkotlin.daemon.jvm.options=$KOTLIN_DAEMON_ARGS"
echo "::endgroup::"

echo "::group::Verify dist + 25 MiB per-file limit"
DIST_DIR="composeApp/build/dist/wasmJs/productionExecutable"
test -f "$DIST_DIR/index.html" || { echo "ERROR: index.html missing in $DIST_DIR" >&2; ls -la "$DIST_DIR"; exit 1; }
test -f "$DIST_DIR/_headers" || { echo "ERROR: _headers missing in $DIST_DIR (expected from wasmJsMain/resources)" >&2; exit 1; }
test -f "$DIST_DIR/init.js" || { echo "ERROR: init.js missing — generateWasmInitJs Gradle task did not run" >&2; exit 1; }

# Cloudflare Workers Static Assets: 25 MiB max per file.
# https://developers.cloudflare.com/workers/static-assets/limits-and-pricing/
MAX_BYTES=$(find "$DIST_DIR" -type f -printf '%s\n' | sort -n | tail -1)
LIMIT=$((25 * 1024 * 1024))
if [ "$MAX_BYTES" -gt "$LIMIT" ]; then
	echo "ERROR: file > 25 MiB ($MAX_BYTES bytes) — Cloudflare assets will reject upload." >&2
	echo "Largest files:" >&2
	find "$DIST_DIR" -type f -printf '%s\t%p\n' | sort -n | tail -5 >&2
	exit 1
fi

echo "Dist size:"
du -sh "$DIST_DIR"
echo "Largest files:"
find "$DIST_DIR" -type f -printf '%s\t%p\n' | sort -n | tail -5
echo "::endgroup::"

echo "Build OK — wrangler deploy will pick up $DIST_DIR via wrangler.jsonc"
