#!/usr/bin/env bash
set -euo pipefail

# Runs the SonarQube analysis and fails when the quality gate fails.
# CI runs it against a temporary SonarQube container (see the "test" job in
# .github/workflows/build.yml). It also works against a local server:
#
#   docker run -d --name sonarqube -p 9000:9000 sonarqube:community
#   export SONAR_ADMIN_PASSWORD='<a new admin password>'
#   SONAR_TOKEN="$(./scripts/sonar-setup.sh)" ./scripts/sonar-scan.sh
#
# Set SONAR_HOST_URL for a server that is not on http://localhost:9000.
# Set SONAR_SKIP_BUILD=true when the tests, coverage reports, and libraries
# are already built, as in CI.
#
# The issues and measures are saved in target/sonar-report/, also when the
# quality gate fails, because a temporary server keeps no results.

: "${SONAR_TOKEN:?Set SONAR_TOKEN. scripts/sonar-setup.sh prints one.}"
SONAR_HOST_URL="${SONAR_HOST_URL:-http://localhost:9000}"
PROJECT_KEY="techbyavanti_recommendation-engine"
REPORT_DIR="target/sonar-report"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

if [[ "${SONAR_SKIP_BUILD:-false}" != "true" ]]; then
  # Compiled classes, JaCoCo reports, and libraries for Java, Vitest
  # coverage for TypeScript. The libraries are copied in the same Maven run,
  # so the reactor supplies the internal rec-support jar.
  mvn -B -q verify dependency:copy-dependencies -DincludeScope=test \
    -Dtest='!*SmokeTest' -Dsurefire.failIfNoSpecifiedTests=false
  (cd web && npm test -- --coverage)
fi

gate_status=0
docker run --rm --network host \
  -e SONAR_HOST_URL="${SONAR_HOST_URL}" \
  -e SONAR_TOKEN="${SONAR_TOKEN}" \
  -v "${ROOT_DIR}:/usr/src" \
  sonarsource/sonar-scanner-cli \
  -Dsonar.qualitygate.wait=true || gate_status=$?

mkdir -p "${REPORT_DIR}"
# A token is the user name, with an empty password.
TOKEN_LOGIN="${SONAR_TOKEN}:"
save_api_response() {
  curl -fsS --user "${TOKEN_LOGIN}" -o "${REPORT_DIR}/$1" "${SONAR_HOST_URL}/api/$2"
}
save_api_response measures.json "measures/component?component=${PROJECT_KEY}&metricKeys=bugs,vulnerabilities,code_smells,security_hotspots,coverage,duplicated_lines_density,ncloc,alert_status"
save_api_response issues.json "issues/search?componentKeys=${PROJECT_KEY}&resolved=false&ps=500"
save_api_response hotspots.json "hotspots/search?project=${PROJECT_KEY}&ps=500"

python3 "${ROOT_DIR}/scripts/sonar_report.py" "${REPORT_DIR}" > "${REPORT_DIR}/summary.md"
cat "${REPORT_DIR}/summary.md"
exit "${gate_status}"
