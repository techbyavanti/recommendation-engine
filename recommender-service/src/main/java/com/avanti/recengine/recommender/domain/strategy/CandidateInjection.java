package com.avanti.recengine.recommender.domain.strategy;

import com.avanti.recengine.recommender.domain.ScoredProduct;
import com.avanti.recengine.recommender.port.out.ClickstreamRepositoryPort;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Appends products that search-service did not return — a real add, not
 * just a rerank. Shared by the strategies that inject their own candidates.
 */
final class CandidateInjection {

    private CandidateInjection() {
    }

    /**
     * Appends up to {@code maxInjected} of {@code candidateIds}, in order,
     * skipping products already in {@code ranked} and duplicates. An id
     * counts toward the limit even when the catalog has no entry for it.
     * The stream is lazy, so candidates after the limit are never computed.
     */
    static void appendAbsent(List<ScoredProduct> ranked, Stream<String> candidateIds, int maxInjected,
                             ClickstreamRepositoryPort clickstream) {
        Set<String> present = ranked.stream().map(ScoredProduct::productId).collect(Collectors.toSet());
        candidateIds
                .filter(id -> !present.contains(id))
                .distinct()
                .limit(maxInjected)
                .toList()
                .forEach(id -> clickstream.catalogEntry(id).ifPresent(entry -> ranked.add(entry.toScoredProduct(0.0))));
    }
}
