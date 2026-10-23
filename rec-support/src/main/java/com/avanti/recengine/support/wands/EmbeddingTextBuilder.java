package com.avanti.recengine.support.wands;

import java.util.StringJoiner;

/**
 * Builds the text passed to the embedding model for a product. Deliberately
 * excludes {@code productFeatures} (pipe-delimited attribute:value pairs) —
 * that attribute noise dilutes sentence-embedding quality more than it helps;
 * it's still stored as separate Pinecone metadata. Ported from the sibling
 * {@code search} project's {@code EmbeddingTextBuilder} — same field
 * composition, so the two projects' embeddings stay comparable in spirit
 * even though the models and vector stores differ.
 */
public final class EmbeddingTextBuilder {

    private static final int MAX_DESCRIPTION_CHARS = 1200;

    private EmbeddingTextBuilder() {
    }

    public static String build(WandsProductRow product) {
        return build(product.productName(), product.productClass(), product.categoryHierarchy(), product.productDescription());
    }

    public static String build(String productName, String productClass, String categoryHierarchy, String productDescription) {
        StringBuilder text = new StringBuilder(productName);
        if (productClass != null) {
            text.append(". ").append(productClass);
        }
        if (categoryHierarchy != null) {
            text.append(". ").append(formatHierarchy(categoryHierarchy));
        }
        if (productDescription != null) {
            text.append(". ").append(truncate(productDescription, MAX_DESCRIPTION_CHARS));
        }
        return text.toString();
    }

    /**
     * Source category hierarchies are inconsistently spaced around "/" (e.g. "Furniture / Beds"
     * vs "Furniture/Beds"), so each "/" and the whitespace next to it becomes " > ". Whitespace
     * at the start and end of the whole value stays. A split, not a regex, keeps this linear.
     */
    private static String formatHierarchy(String categoryHierarchy) {
        String[] levels = categoryHierarchy.split("/", -1);
        StringJoiner formatted = new StringJoiner(" > ");
        for (int i = 0; i < levels.length; i++) {
            String level = levels[i];
            if (i > 0) {
                level = level.stripLeading();
            }
            if (i < levels.length - 1) {
                level = level.stripTrailing();
            }
            formatted.add(level);
        }
        return formatted.toString();
    }

    private static String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}
