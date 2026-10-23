package com.avanti.recengine.support.pinecone;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/** A vector to upsert: an id, its embedding, and arbitrary metadata (String/Number/Boolean values only). */
public record ProductVector(String id, float[] values, Map<String, Object> metadata) {

    // A record compares array components by reference. These overrides compare the embedding values.

    @Override
    public boolean equals(Object other) {
        return other instanceof ProductVector that
                && Objects.equals(id, that.id)
                && Arrays.equals(values, that.values)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, Arrays.hashCode(values), metadata);
    }

    @Override
    public String toString() {
        return "ProductVector[id=" + id + ", values=" + Arrays.toString(values) + ", metadata=" + metadata + "]";
    }
}
