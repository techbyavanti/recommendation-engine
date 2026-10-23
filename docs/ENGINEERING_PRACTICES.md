# Engineering practices

This document describes how the project controls quality, security, and dependencies.
For the code structure, see [ARCHITECTURE.md](ARCHITECTURE.md).
For the main technical decisions, see [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md).

## Tests

The project has more than 100 Java tests, plus frontend and Python tests. Each layer has a different type of test.

| Layer | Test type | Example |
|---|---|---|
| Domain and application logic | Unit tests with hand-written fake ports. No Spring, no network, no Docker. | `CollaborativeFilteringStrategyTest`, `SearchOrchestrationUseCaseTest` |
| gRPC adapters | In-process gRPC server and channel. The tests use real message serialization, but no real sockets. | `SearchGrpcServiceTest`, `RecommenderGrpcServiceTest` |
| GraphQL API | Spring `@GraphQlTest` with a mock use case | `SearchGraphqlControllerTest` |
| Training and serving agreement | Java and Python must calculate identical features from one shared fixture file | `FeatureParityTest`, `training/test_feature_parity.py` |
| External systems | Tests against a live Pinecone Local container and the real embedding model. They skip when these are not available. | `PineconeVectorStoreSmokeTest`, `EmbeddingServiceTest` |
| Frontend | Component tests with Vitest and Testing Library | `web/src/App.test.tsx` |
| Full system | Docker Compose stack and real GraphQL queries | Manual, see [HOWTO.md](../HOWTO.md) |

Most logic is in the domain and application layers. These tests run in milliseconds.

## Continuous integration

GitHub Actions runs [`.github/workflows/build.yml`](../.github/workflows/build.yml) on each pull request and on each push to `main`. A pull request must pass all jobs.

| Job | What it does | Fails on |
|---|---|---|
| `test` | Runs the Java tests, the frontend tests, and the production frontend build, with coverage. Then runs the SonarQube analysis. | Any test or build failure, or a failed SonarQube quality gate |
| `cve-scan` | Scans the Maven and npm dependencies, the Dockerfiles, and all files with Trivy | Critical, high, or medium CVEs, Dockerfile misconfigurations, or secrets |
| `sast` | Runs Semgrep static analysis on the Java and TypeScript code | Any security finding |
| `secret-scan` | Scans the full Git history with Gitleaks | Any committed secret |
| `docker-lint` | Lints each Dockerfile with Hadolint | Any warning |
| `docker-image-scan` | Builds each container image, scans it with Trivy, and makes a CycloneDX SBOM | Critical or high CVEs, or medium CVEs that have a fix |

The scan jobs also send their results to the GitHub Security tab in SARIF format.

## Code quality

SonarQube analyzes the Java, TypeScript, Python, and shell code, the Dockerfiles, and the Compose file. [`sonar-project.properties`](../sonar-project.properties) gives the source, test, and coverage paths.

| Item | Details |
|---|---|
| Where it runs | SonarQube Cloud, in the CI `test` job. The step runs only when the `SONAR_TOKEN` repository secret is set. |
| Quality gate | The default gate applies to new code: no new issues, all security hotspots reviewed, at least 80% coverage, and at most 3% duplicated lines. |
| Coverage | JaCoCo for Java (`mvn verify`) and Vitest for TypeScript (`npm test -- --coverage`) |
| Local analysis | `scripts/sonar-scan.sh` runs the same analysis against a local SonarQube server |

To connect SonarQube Cloud:

1. Import the repository at [sonarcloud.io](https://sonarcloud.io) into the `techbyavanti` organization.
2. In the project settings, turn off automatic analysis. The CI step does the analysis.
3. Create a token, and add it as the `SONAR_TOKEN` repository secret in GitHub.

Rules for clean code:

- Fix a finding in the code. Do not change the rule or the quality gate.
- When a finding does not apply, add `NOSONAR` to the line, with a comment that gives the reason. For example, the training script uses a seeded random generator for a reproducible split, not for security.
- Before a change in behavior-sensitive code, add a test that records the current behavior. For example, `EmbeddingTextBuilderTest` recorded the category format before a regular expression was replaced.

## Security

- **Dependency pins with a reason.** When a transitive dependency has a known CVE, the parent `pom.xml` pins a fixed version. A comment gives the CVE numbers and the reason for the version.
- **Non-root containers.** The three Java images run as user `1000`. The web image uses the unprivileged nginx image, which runs as user `101` on port 8080.
- **Health checks.** Each image has a `HEALTHCHECK`. The Java images check that their port accepts connections. The web image requests its home page.
- **Base image patches.** Each Dockerfile upgrades all OS packages. A fix that is in the Ubuntu or Alpine security index then applies before a new base image is published.
- **No secrets in the repository.** The local stack uses Pinecone Local, which does not need an API key. Configuration comes from environment variables.

## Dependency management

- **Dependabot** proposes updates each week for Maven, npm, Docker base images, and GitHub Actions. It groups minor and patch updates into one pull request.
- **Ignore rules with reasons.** [`.github/dependabot.yml`](../.github/dependabot.yml) blocks updates that are known to break the system. For example, `pinecone-client` 4.x does not work with Pinecone Local. A comment gives the reason for each rule.
- **Major upgrades are tested.** A major upgrade gets its own pull request and a full-system test. The upgrade to Spring Boot 4 is an example.

## Reproducible results

- The offline evaluation is deterministic. The same code and data always give the same `RESULTS.md`.
- Random processes use fixed seeds: the held-out user split, the bandit evaluation, and model training.
- The evaluation uses only data from before each session. Future data cannot affect a result.
- `FeatureParityTest` makes sure that the served model receives the same features that it trained on.

## Examples from the project history

These changes show how the project finds and corrects problems.

| Problem | Detection | Correction |
|---|---|---|
| Critical CVEs in Spring WebMVC, with no fix for Spring Framework 6 | CI container scan | Upgrade to Spring Boot 4 and Spring Framework 7. Replace the gRPC starter, which supported only Spring Boot 3. Test the full system. |
| Bandit and Neural ranking results changed between runs | Two evaluation runs gave different numbers | A category tie-break used a map with a random iteration order. The fix uses first-interaction order, the same rule as the Python training code. |
| The same aggregation code was in two classes | Code review | One shared base class, `ClickstreamAggregates`. A full evaluation run before and after the change gave identical results. |
| High-severity Netty CVEs from a transitive dependency | CI dependency scan | A pinned Netty version in the patched 4.1 line, with a comment that gives the CVE numbers |
| A script sent a command-line URL to `urlopen`, which also accepts `file://` URLs | Semgrep in CI | Accept only `http` and `https` URLs before the request |
| The web image ran as root and no image had a health check | Trivy misconfiguration scan | The unprivileged nginx image, and a `HEALTHCHECK` in each Dockerfile |
| A failed search request also caused an unhandled promise rejection in the browser | SonarQube | A `catch` on the request promise. The hook's error state still shows the error. |
| `ProductVector` compared its embedding array by reference | SonarQube | `equals`, `hashCode`, and `toString` that use the array values |

## Documentation

- [README.md](../README.md) gives a short summary and links to all documents.
- Each document has one topic and one main reader. The README [documentation table](../README.md#documentation) lists them.
- Code comments explain why the code does something. They do not repeat what the code does.
