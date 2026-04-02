#!/usr/bin/env bash
# =============================================================================
# setup-env.sh — ZTE-Lightweight environment health checker
#
# Verifies that Docker services (Keycloak + PostgreSQL) are reachable and
# that the Gradle build toolchain is functional.
#
# Usage:
#   chmod +x setup-env.sh
#   ./setup-env.sh
#
# Exit codes:
#   0  All checks passed
#   1  One or more checks failed
# =============================================================================

set -euo pipefail

# ── Colours ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
PASS="${GREEN}[PASS]${NC}"; FAIL="${RED}[FAIL]${NC}"; WARN="${YELLOW}[WARN]${NC}"

FAILURES=0

log_pass() { echo -e "${PASS} $*"; }
log_fail() { echo -e "${FAIL} $*"; (( FAILURES++ )) || true; }
log_warn() { echo -e "${WARN} $*"; }
log_info() { echo -e "       $*"; }

# ── Helper: check TCP port reachability ──────────────────────────────────────
check_port() {
    local host="$1" port="$2" label="$3"
    if timeout 3 bash -c "echo > /dev/tcp/${host}/${port}" 2>/dev/null; then
        log_pass "${label} is reachable at ${host}:${port}"
    else
        log_fail "${label} is NOT reachable at ${host}:${port}"
    fi
}

# ── Helper: check HTTP endpoint ───────────────────────────────────────────────
check_http() {
    local url="$1" label="$2" expected_code="${3:-200}"
    local actual_code
    if command -v curl &>/dev/null; then
        actual_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${url}" || echo "000")
        if [[ "${actual_code}" == "${expected_code}" ]]; then
            log_pass "${label} responded HTTP ${actual_code}"
        else
            log_fail "${label} responded HTTP ${actual_code} (expected ${expected_code}) — URL: ${url}"
        fi
    else
        log_warn "curl not found — skipping HTTP check for ${label}"
    fi
}

echo ""
echo "═══════════════════════════════════════════════════════"
echo "  ZTE-Lightweight — Environment Health Check"
echo "═══════════════════════════════════════════════════════"
echo ""

# ── 1. Docker ─────────────────────────────────────────────────────────────────
echo "── Docker ──────────────────────────────────────────────"
if command -v docker &>/dev/null; then
    log_pass "Docker CLI found: $(docker --version)"
    if docker info &>/dev/null; then
        log_pass "Docker daemon is running"
    else
        log_fail "Docker daemon is NOT running — start Docker Desktop"
    fi
else
    log_fail "Docker CLI not found in PATH"
fi
echo ""

# ── 2. Containers running ────────────────────────────────────────────────────
echo "── Docker Containers ───────────────────────────────────"
if command -v docker &>/dev/null && docker info &>/dev/null; then
    for container in zte-postgres zte-keycloak; do
        status=$(docker inspect --format='{{.State.Health.Status}}' "${container}" 2>/dev/null || echo "not_found")
        case "${status}" in
            healthy)   log_pass "Container ${container}: healthy" ;;
            starting)  log_warn "Container ${container}: still starting — re-run this script in 30s" ;;
            not_found) log_fail "Container ${container}: not found — run: docker compose up -d" ;;
            *)         log_fail "Container ${container}: status=${status}" ;;
        esac
    done
else
    log_warn "Docker unavailable — skipping container checks"
fi
echo ""

# ── 3. Port checks ───────────────────────────────────────────────────────────
echo "── Port Connectivity ───────────────────────────────────"
check_port "localhost" "5432"  "PostgreSQL"
check_port "localhost" "8180"  "Keycloak"
echo ""

# ── 4. Keycloak HTTP health ───────────────────────────────────────────────────
echo "── Keycloak Health Endpoint ─────────────────────────────"
check_http "http://localhost:8180/health/ready" "Keycloak /health/ready" "200"
check_http "http://localhost:8180/realms/zte/.well-known/openid-configuration" \
           "Keycloak realm 'zte' OIDC discovery" "200"
log_info "Note: the 'zte' realm must be created manually in Keycloak admin (http://localhost:8180)"
echo ""

# ── 5. Java toolchain ─────────────────────────────────────────────────────────
echo "── Java Toolchain ──────────────────────────────────────"
if command -v java &>/dev/null; then
    java_version=$(java -version 2>&1 | head -1)
    major=$(java -version 2>&1 | grep -oP '(?<=version ")[0-9]+' || echo "0")
    if (( major >= 21 )); then
        log_pass "Java ${major}: ${java_version}"
    else
        log_fail "Java ${major} detected — Java 21+ required. Version: ${java_version}"
    fi
else
    log_fail "Java not found in PATH — install JDK 21"
fi
echo ""

# ── 6. Gradle wrapper ────────────────────────────────────────────────────────
echo "── Gradle Build ────────────────────────────────────────"
if [[ -f "./gradlew" ]]; then
    log_pass "gradlew found"
    echo "       Running: ./gradlew build --no-daemon -q ..."
    if ./gradlew build --no-daemon -q 2>&1; then
        log_pass "Gradle build succeeded"
    else
        log_fail "Gradle build FAILED — check output above"
    fi
else
    log_fail "gradlew not found — run: gradle wrapper --gradle-version 8.12"
fi
echo ""

# ── Summary ──────────────────────────────────────────────────────────────────
echo "═══════════════════════════════════════════════════════"
if (( FAILURES == 0 )); then
    echo -e "${GREEN}  All checks passed. ZTE environment is ready.${NC}"
    echo "═══════════════════════════════════════════════════════"
    exit 0
else
    echo -e "${RED}  ${FAILURES} check(s) FAILED. See details above.${NC}"
    echo "═══════════════════════════════════════════════════════"
    exit 1
fi
