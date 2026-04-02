# project-health-check

Run a comprehensive health check on the ZTE-Lightweight project environment.

## What this skill does

1. **Gradle build** — Runs `./gradlew build --no-daemon` and reports success or failure with the compiler output.
2. **Docker containers** — Checks that `zte-postgres` and `zte-keycloak` containers exist and reports their health status.
3. **Port availability** — Verifies that PostgreSQL (5432) and Keycloak (8180) ports are accepting connections.
4. **Keycloak OIDC** — Fetches `http://localhost:8180/health/ready` and the `zte` realm discovery endpoint.
5. **Java toolchain** — Confirms Java 21+ is on the PATH.

## Steps

Run the following checks in order and report results clearly:

### Step 1 — Gradle Build
```bash
cd /mnt/c/Users/User/CodeProjects/ZTE && ./gradlew build --no-daemon 2>&1
```
Report: PASS if exit code 0, FAIL with truncated error output otherwise.

### Step 2 — Docker Container Health
```bash
docker inspect --format='{{.Name}}: {{.State.Health.Status}}' zte-postgres zte-keycloak 2>&1
```
Report: each container's status (healthy / starting / unhealthy / not_found).

### Step 3 — Port Checks
Check if ports are open using bash TCP:
```bash
timeout 3 bash -c 'echo > /dev/tcp/localhost/5432' && echo "PostgreSQL:OPEN" || echo "PostgreSQL:CLOSED"
timeout 3 bash -c 'echo > /dev/tcp/localhost/8180' && echo "Keycloak:OPEN" || echo "Keycloak:CLOSED"
```

### Step 4 — Keycloak HTTP Health
```bash
curl -sf http://localhost:8180/health/ready && echo "KC_HEALTH:OK" || echo "KC_HEALTH:FAIL"
curl -sf http://localhost:8180/realms/zte/.well-known/openid-configuration | python3 -c "import sys,json; d=json.load(sys.stdin); print('KC_REALM_ZTE:OK issuer='+d['issuer'])" 2>/dev/null || echo "KC_REALM_ZTE:FAIL (realm may not exist yet)"
```

### Step 5 — Java Version
```bash
java -version 2>&1
```
Report: PASS if version >= 21, FAIL otherwise.

## Output format

Present results as a markdown table:

| Check | Status | Detail |
|---|---|---|
| Gradle Build | PASS/FAIL | ... |
| zte-postgres | healthy/FAIL | ... |
| zte-keycloak | healthy/FAIL | ... |
| PostgreSQL port 5432 | OPEN/CLOSED | ... |
| Keycloak port 8180 | OPEN/CLOSED | ... |
| Keycloak /health/ready | OK/FAIL | ... |
| Keycloak realm 'zte' | OK/FAIL | ... |
| Java version | PASS/FAIL | version string |

End with a summary: **All systems go** or list what needs attention.

## Troubleshooting hints

- If Docker containers are missing: `docker compose -f /mnt/c/Users/User/CodeProjects/ZTE/docker-compose.yml up -d`
- If Gradle fails: check Java 21 is installed and `JAVA_HOME` points to JDK 21
- If Keycloak realm 'zte' is missing: log into http://localhost:8180 (admin/admin) and create it manually
- If gradlew is not executable: `chmod +x /mnt/c/Users/User/CodeProjects/ZTE/gradlew`
