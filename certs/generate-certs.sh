#!/usr/bin/env bash
# =============================================================================
# ZTE Development Certificate Generator
# =============================================================================
# Generates a self-signed CA and service certificates for local mTLS development.
#
# Prerequisites: openssl >= 1.1.1, keytool (JDK)
#
# Output (all in this directory):
#   ca.crt              — ZTE root CA certificate (public, safe to distribute)
#   ca.key              — ZTE CA private key (keep secret)
#   client.p12          — PKCS12 keystore for internal client auth
#                         (used by gateway + service-a for outbound mTLS calls)
#   service-a.p12       — PKCS12 keystore for service-a's HTTPS server
#   service-b.p12       — PKCS12 keystore for service-b's HTTPS server
#   truststore.p12      — PKCS12 truststore containing only the ZTE CA cert
#                         (used by all services to trust peer certificates)
#
# Usage:
#   chmod +x certs/generate-certs.sh
#   ./certs/generate-certs.sh
# =============================================================================
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PASS="${ZTE_KEY_PASSWORD:-zte-pass}"
DAYS_CA=3650   # 10 years — root CA, rotate with full PKI overhaul
DAYS_SVC=365   # 1 year — service certs; automate rotation via CI/CD
SUBJ_BASE="/C=IL/ST=Dev/L=Dev/O=ZTE-Lightweight"

# Colour helpers
GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }

info "Generating ZTE development certificates in: $DIR"
info "Key password: $PASS (override with ZTE_KEY_PASSWORD env var)"
cd "$DIR"

# ── Verify tooling ──────────────────────────────────────────────────────────
for cmd in openssl keytool; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "ERROR: '$cmd' not found. Install OpenSSL and a JDK." >&2
        exit 1
    fi
done

# ── 1. ZTE Root CA ──────────────────────────────────────────────────────────
info "1/5  Generating ZTE Root CA (${DAYS_CA}d) ..."
openssl req -x509 -newkey rsa:4096 \
    -keyout ca.key -out ca.crt \
    -days $DAYS_CA -nodes \
    -subj "${SUBJ_BASE}/CN=ZTE-CA"

# ── 2. Internal Client Certificate ─────────────────────────────────────────
# Used by gateway (outbound to service-a/b) and service-a (outbound to service-b)
info "2/5  Generating internal client certificate ..."
openssl req -newkey rsa:2048 \
    -keyout client.key -out client.csr \
    -nodes -subj "${SUBJ_BASE}/CN=zte-internal-client"

openssl x509 -req \
    -in client.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out client.crt -days $DAYS_SVC \
    -extfile <(echo "extendedKeyUsage=clientAuth")

openssl pkcs12 -export \
    -in client.crt -inkey client.key \
    -certfile ca.crt \
    -out client.p12 -passout "pass:${PASS}" \
    -name "zte-internal-client"

# ── 3. Service A Server Certificate ────────────────────────────────────────
info "3/5  Generating service-a server certificate ..."
openssl req -newkey rsa:2048 \
    -keyout service-a.key -out service-a.csr \
    -nodes -subj "${SUBJ_BASE}/CN=service-a"

openssl x509 -req \
    -in service-a.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out service-a.crt -days $DAYS_SVC \
    -extfile <(printf "subjectAltName=DNS:service-a,DNS:localhost,IP:127.0.0.1\nextendedKeyUsage=serverAuth")

openssl pkcs12 -export \
    -in service-a.crt -inkey service-a.key \
    -certfile ca.crt \
    -out service-a.p12 -passout "pass:${PASS}" \
    -name "service-a"

# ── 4. Service B Server Certificate ────────────────────────────────────────
info "4/5  Generating service-b server certificate ..."
openssl req -newkey rsa:2048 \
    -keyout service-b.key -out service-b.csr \
    -nodes -subj "${SUBJ_BASE}/CN=service-b"

openssl x509 -req \
    -in service-b.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out service-b.crt -days $DAYS_SVC \
    -extfile <(printf "subjectAltName=DNS:service-b,DNS:localhost,IP:127.0.0.1\nextendedKeyUsage=serverAuth")

openssl pkcs12 -export \
    -in service-b.crt -inkey service-b.key \
    -certfile ca.crt \
    -out service-b.p12 -passout "pass:${PASS}" \
    -name "service-b"

# ── 5. Truststore (CA cert only) ────────────────────────────────────────────
info "5/5  Generating shared truststore (CA cert only) ..."
rm -f truststore.p12
keytool -import -trustcacerts \
    -alias zte-ca \
    -file ca.crt \
    -keystore truststore.p12 \
    -storetype PKCS12 \
    -storepass "${PASS}" \
    -noprompt

# ── Cleanup intermediate files ──────────────────────────────────────────────
rm -f ./*.csr ./*.srl

info "Done! Generated certificate files:"
ls -lh "${DIR}"/*.p12 "${DIR}"/*.crt "${DIR}"/*.key 2>/dev/null || true
echo ""
warn "IMPORTANT: Never commit *.key or *.p12 files. They are gitignored."
warn "Run this script once per dev environment or after cert expiry."
