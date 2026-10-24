package com.avanti.recengine.support.pinecone;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real integration test against a locally-running Pinecone Local container
 * (ghcr.io/pinecone-io/pinecone-local) — not mocked, verifies the actual
 * wire protocol works end-to-end. Skips gracefully if Pinecone Local isn't
 * reachable (e.g. in CI, which runs unit tests only per search's own
 * "needs no Elasticsearch" pattern for the plain `test` job).
 */
class PineconeVectorStoreSmokeTest {

    private static final String CONTROL_PLANE_HOST = "http://localhost:5080";
    private static final String INDEX_NAME = "smoketest-index";
    private static final Duration PINECONE_LOCAL_DELAY = Duration.ofSeconds(10);
    private static final List<ProductVector> VECTORS = List.of(
            new ProductVector("p1", new float[]{1f, 0f, 0f, 0f}, Map.of("name", "chair", "rating", 4.5)),
            new ProductVector("p2", new float[]{0f, 1f, 0f, 0f}, Map.of("name", "table", "rating", 3.2)),
            new ProductVector("p3", new float[]{0.9f, 0.1f, 0f, 0f}, Map.of("name", "armchair", "rating", 4.8)));

    @Test
    void upsertsAndQueriesRealVectors() {
        Assumptions.assumeTrue(pineconeLocalReachable(), "Pinecone Local not reachable on localhost:5080 — skipping");

        PineconeVectorStore.ensureServerlessIndex(CONTROL_PLANE_HOST, "pclocal", false, INDEX_NAME, 4);

        try (PineconeVectorStore store = new PineconeVectorStore(CONTROL_PLANE_HOST, "pclocal", false, INDEX_NAME)) {
            // Pinecone Local's control plane needs a moment to bring the new
            // index's data-plane port up after creation.
            await().atMost(PINECONE_LOCAL_DELAY).ignoreExceptions().untilAsserted(() -> store.upsertBatch(VECTORS));
            // Pinecone Local's upsert is not immediately read-your-writes consistent.
            await().atMost(PINECONE_LOCAL_DELAY)
                    .untilAsserted(() -> assertThat(store.query(new float[]{1f, 0f, 0f, 0f}, 2)).hasSize(2));

            List<ScoredMatch> matches = store.query(new float[]{1f, 0f, 0f, 0f}, 2);

            assertThat(matches.get(0).id()).isEqualTo("p1");
            assertThat(matches.get(0).metadata()).containsEntry("name", "chair").containsEntry("rating", 4.5);

            // queryById: no client-side vector needed, Pinecone looks up
            // p1's stored vector server-side. p1's own nearest neighbor is
            // itself (score 1.0), then p3 (0.9,0.1,0,0 — close to p1), then
            // p2 (orthogonal, furthest).
            List<ScoredMatch> byId = store.queryById("p1", 3);
            assertThat(byId).hasSize(3);
            assertThat(byId.stream().map(ScoredMatch::id).toList()).containsExactly("p1", "p3", "p2");
        }
    }

    private static boolean pineconeLocalReachable() {
        try {
            HttpResponse<Void> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(CONTROL_PLANE_HOST + "/indexes")).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception _) {
            return false;
        }
    }
}
