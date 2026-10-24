package com.avanti.recengine.search.domain.lexical;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Splits text into lowercase alphanumeric tokens. Deliberately simple (no
 * stemming, no stopword removal) — BM25's own IDF term already down-weights
 * high-frequency words like "the"/"and", and stemming would blur the exact
 * token matches this lexical path exists to catch (SKU-shaped tokens, model
 * numbers) in the first place.
 */
final class Tokenizer {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{Nd}]+");

    private Tokenizer() {
    }

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return NON_ALPHANUMERIC.splitAsStream(text.toLowerCase(Locale.ROOT))
                .filter(token -> !token.isEmpty())
                .toList();
    }
}
