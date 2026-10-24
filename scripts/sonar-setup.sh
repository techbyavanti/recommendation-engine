#!/usr/bin/env bash
set -euo pipefail

# Prepares a SonarQube server for scripts/sonar-scan.sh and prints an
# analysis token on stdout. CI runs it against a temporary SonarQube
# container. It also works against a local server.
#
# 1. Waits until the server is UP.
# 2. Replaces the default admin password with SONAR_ADMIN_PASSWORD.
# 3. Makes "Sonar way comprehensive" the rule set for each language.
# 4. Creates the project quality gate and makes it the default gate.
# 5. Prints a new analysis token.
#
# Usage: SONAR_ADMIN_PASSWORD=<password> ./scripts/sonar-setup.sh
# The password needs at least 12 characters, with an uppercase letter, a
# lowercase letter, a digit, and a special character.

: "${SONAR_ADMIN_PASSWORD:?Set SONAR_ADMIN_PASSWORD to the admin password to use.}"
SONAR_HOST_URL="${SONAR_HOST_URL:-http://localhost:9000}"
GATE_NAME="Recommendation Engine"
RULE_SET="Sonar way comprehensive"
# SonarQube's documented first-login credentials, not a secret.
FIRST_LOGIN="admin:admin"
ADMIN_LOGIN="admin:${SONAR_ADMIN_PASSWORD}"

# GET request as admin.
api_get() {
  curl -fsS --user "${ADMIN_LOGIN}" "${SONAR_HOST_URL}/api/$1"
}

# POST request as admin.
api() {
  curl -fsS --user "${ADMIN_LOGIN}" -X POST "${SONAR_HOST_URL}/api/$1" "${@:2}"
}

for _ in $(seq 1 60); do
  if curl -fsS "${SONAR_HOST_URL}/api/system/status" 2>/dev/null | grep -q '"status":"UP"'; then
    break
  fi
  sleep 5
done
curl -fsS "${SONAR_HOST_URL}/api/system/status" | grep -q '"status":"UP"' ||
  { echo "SonarQube at ${SONAR_HOST_URL} did not start." >&2; exit 1; }

# A new server accepts only admin/admin. A server that ran this script before
# rejects the default password, so the change is skipped.
curl -fsS -o /dev/null --user "${FIRST_LOGIN}" -X POST "${SONAR_HOST_URL}/api/users/change_password" \
  --data-urlencode login=admin \
  --data-urlencode previousPassword=admin \
  --data-urlencode "password=${SONAR_ADMIN_PASSWORD}" 2>/dev/null || true

# A new server uses the small "Sonar way core" rule sets, for example 274 of
# the 618 Java rules. The comprehensive rule sets include all the clean-code
# rules.
api_get qualityprofiles/search |
  python3 -c 'import json, sys
for p in json.load(sys.stdin)["profiles"]:
    if p["name"] == sys.argv[1]:
        print(p["language"])' "${RULE_SET}" |
  while read -r language; do
    api qualityprofiles/set_default -d "language=${language}" --data-urlencode "qualityProfile=${RULE_SET}" >/dev/null
  done

# The built-in gate checks only new code: code that changed since the last
# analysis. A temporary server has no last analysis, so that gate always
# passes. This gate checks all code. The coverage limit is a few points
# below the CI value (54.6%), so coverage cannot go down much. CI coverage
# is lower than local coverage, because CI has no embedding model for
# EmbeddingServiceTest. SonarQube also adds its
# standard new-code conditions to a new gate. They have no effect here.
if ! api_get qualitygates/list |
    grep -q "\"name\":\"${GATE_NAME}\""; then
  api qualitygates/create --data-urlencode "name=${GATE_NAME}" >/dev/null
  while read -r metric op limit; do
    api qualitygates/create_condition --data-urlencode "gateName=${GATE_NAME}" \
      -d "metric=${metric}" -d "op=${op}" -d "error=${limit}" >/dev/null
  done <<'CONDITIONS'
bugs GT 0
vulnerabilities GT 0
code_smells GT 0
security_hotspots_reviewed LT 100
duplicated_lines_density GT 3
coverage LT 50
CONDITIONS
fi
api qualitygates/set_as_default --data-urlencode "name=${GATE_NAME}" >/dev/null

api user_tokens/generate --data-urlencode "name=analysis-$(date +%s)" |
  python3 -c 'import json, sys; print(json.load(sys.stdin)["token"])'
