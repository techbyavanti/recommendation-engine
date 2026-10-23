#!/usr/bin/env bash
set -euo pipefail

# Runs the SonarQube analysis of the CI "test" job against a local
# SonarQube server, so findings are reproducible before they reach CI.
#
# Start a server once, then create a token in its UI (My Account > Security):
#   docker run -d --name sonarqube -p 9000:9000 sonarqube:community
#
# Usage: SONAR_TOKEN=<token> ./scripts/sonar-scan.sh
# Set SONAR_HOST_URL for a server that is not on http://localhost:9000.

: "${SONAR_TOKEN:?Set SONAR_TOKEN to a token from your SonarQube server.}"
SONAR_HOST_URL="${SONAR_HOST_URL:-http://localhost:9000}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

# Compiled classes, JaCoCo reports, and libraries for the Java analysis.
mvn -B -q verify -Dtest='!*SmokeTest' -Dsurefire.failIfNoSpecifiedTests=false
mvn -B -q dependency:copy-dependencies -DincludeScope=test

# Vitest coverage for the TypeScript analysis.
(cd web && npm test -- --coverage)

# sonar.organization applies only to SonarQube Cloud, so it is cleared here.
docker run --rm --network host \
  -e SONAR_HOST_URL="${SONAR_HOST_URL}" \
  -e SONAR_TOKEN="${SONAR_TOKEN}" \
  -v "${ROOT_DIR}:/usr/src" \
  sonarsource/sonar-scanner-cli \
  -Dsonar.organization= \
  -Dsonar.qualitygate.wait=true
