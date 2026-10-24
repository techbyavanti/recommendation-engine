package com.avanti.recengine.support.pinecone;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductVectorTest {

    @Test
    void vectorsWithEqualValuesAreEqual() {
        ProductVector first = new ProductVector("p1", new float[]{1f, 2f}, Map.of("name", "chair"));
        ProductVector second = new ProductVector("p1", new float[]{1f, 2f}, Map.of("name", "chair"));

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second).hasToString(second.toString());
    }

    @Test
    void vectorsWithDifferentValuesAreNotEqual() {
        ProductVector first = new ProductVector("p1", new float[]{1f, 2f}, Map.of());
        ProductVector second = new ProductVector("p1", new float[]{1f, 3f}, Map.of());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void toStringShowsTheValues() {
        assertThat(new ProductVector("p1", new float[]{1f, 2f}, Map.of())).hasToString("ProductVector[id=p1, values=[1.0, 2.0], metadata={}]");
    }
}
