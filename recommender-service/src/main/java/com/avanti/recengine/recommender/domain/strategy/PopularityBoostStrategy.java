package com.avanti.recengine.recommender.domain.strategy;

import com.avanti.recengine.recommender.domain.RecommendationContext;
import com.avanti.recengine.recommender.domain.RecommendationStrategy;
import com.avanti.recengine.recommender.domain.ScoredProduct;
import com.avanti.recengine.recommender.port.out.ClickstreamRepositoryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reranks by blending search-service's own score with clickstream
 * popularity, then injects globally popular "trending" products absent from
 * the original candidates — a real add, not just a rerank, per the "add/
 * remove results from search-service" requirement.
 *
 * <p>Popularity-based candidate generation/backfill is the most common
 * production baseline recommender pattern in practice — see
 * arXiv:2509.06002 ("A Survey of Real-World Recommender Systems:
 * Challenges, Constraints, and Industrial Perspectives"), which discusses
 * exactly this kind of cheap, robust fallback signal as a standard
 * component of real candidate-generation-then-rerank pipelines, not a
 * naive strawman.
 */
public final class PopularityBoostStrategy implements RecommendationStrategy {

    private static final double SEARCH_SCORE_WEIGHT = 0.6;
    private static final double POPULARITY_WEIGHT = 0.4;
    private static final int MAX_INJECTED = 2;

    private final ClickstreamRepositoryPort clickstream;

    public PopularityBoostStrategy(ClickstreamRepositoryPort clickstream) {
        this.clickstream = clickstream;
    }

    @Override
    public List<ScoredProduct> apply(RecommendationContext context, List<ScoredProduct> baseResults) {
        if (baseResults.isEmpty()) {
            return List.of();
        }

        Map<ScoredProduct, Double> normalizedSearchScore = Normalization.minMax(baseResults, ScoredProduct::score);
        Map<ScoredProduct, Double> normalizedPopularity =
                Normalization.minMax(baseResults, p -> clickstream.popularityScore(p.productId()));

        List<ScoredProduct> reranked = new ArrayList<>(baseResults.size());
        for (ScoredProduct product : baseResults) {
            double blended = SEARCH_SCORE_WEIGHT * normalizedSearchScore.get(product)
                    + POPULARITY_WEIGHT * normalizedPopularity.get(product);
            reranked.add(product.withScore(blended));
        }
        reranked.sort(ScoredProduct.BY_SCORE_DESCENDING);

        // Enough popular products that MAX_INJECTED remain after the ones already present.
        List<String> popular = clickstream.mostPopularProducts(MAX_INJECTED + reranked.size());
        CandidateInjection.appendAbsent(reranked, popular.stream(), MAX_INJECTED, clickstream);
        return reranked;
    }

    @Override
    public String name() {
        return "Popularity";
    }
}
