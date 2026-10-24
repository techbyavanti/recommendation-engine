# Build stage: matches the local toolchain (Maven 3.9.x, JDK 26) exactly.
FROM maven:3.9.16-eclipse-temurin-26 AS build
WORKDIR /workspace

COPY pom.xml .
COPY rec-support/pom.xml rec-support/
COPY search-service/pom.xml search-service/
COPY recommender-service/pom.xml recommender-service/
COPY graphql-gateway/pom.xml graphql-gateway/
COPY proto proto
RUN mvn -q -B -pl search-service -am dependency:go-offline

COPY rec-support/src rec-support/src
COPY search-service/src search-service/src
RUN mvn -q -B -pl search-service -am package -DskipTests

# Runtime stage: JRE only, glibc-based (jammy, not alpine) — ONNX Runtime's
# and the tokenizers JNI binding's native libraries are glibc-linked.
FROM eclipse-temurin:26-jre-jammy
# The published base image often lags behind Ubuntu's security index. For
# example, it shipped libssl3 3.0.2-0ubuntu1.29 (CVE-2026-84782, fixed in
# 1.30), libfreetype6 2.11.1+dfsg-1ubuntu0.3 (CVE-2026-95512) and
# libpng16-16 1.6.37-3ubuntu0.6 (CVE-2026-46675). Upgrade all packages, so
# each new fix applies without a change to this file.
# One layer for the package upgrade and the non-root user.
RUN apt-get update && \
    apt-get upgrade -y --no-install-recommends && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system --gid 1000 recengine && \
    useradd --system --uid 1000 --gid recengine --no-create-home recengine
WORKDIR /app
COPY --from=build /workspace/search-service/target/search-service.jar app.jar
# Also include the shaded ingestion CLI jar, so `docker compose run
# --entrypoint java search-service -jar ingestion-cli.jar` can populate
# Pinecone from inside the compose network — the CLI needs to resolve
# `pinecone-local` as a hostname, which only works from within the network,
# not from the host (Pinecone Local advertises its container-internal
# hostname as the data-plane host regardless of caller).
COPY --from=build /workspace/search-service/target/search-service-*-ingestion-cli.jar ingestion-cli.jar
RUN chown recengine:recengine /app/app.jar /app/ingestion-cli.jar
USER 1000:1000
EXPOSE 9090
# Liveness: the server accepts TCP connections on its port. The JRE image
# has no curl, so bash's /dev/tcp opens the connection. The port variable
# matches the one that application.yml reads.
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${GRPC_PORT:-9090}"]
ENTRYPOINT ["java", "-jar", "app.jar"]
