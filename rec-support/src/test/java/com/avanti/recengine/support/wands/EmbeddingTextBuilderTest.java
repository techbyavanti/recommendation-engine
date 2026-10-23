package com.avanti.recengine.support.wands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingTextBuilderTest {

    @Test
    void joinsNameClassHierarchyAndDescription() {
        String text = EmbeddingTextBuilder.build("oak bed", "Beds", "Furniture / Beds", "A solid bed.");

        assertThat(text).isEqualTo("oak bed. Beds. Furniture > Beds. A solid bed.");
    }

    @Test
    void skipsMissingFields() {
        assertThat(EmbeddingTextBuilder.build("oak bed", null, null, null)).isEqualTo("oak bed");
    }

    @Test
    void truncatesLongDescriptions() {
        String text = EmbeddingTextBuilder.build("bed", null, null, "x".repeat(2000));

        assertThat(text).hasSize("bed. ".length() + 1200);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "Furniture / Beds|Furniture > Beds",
            "Furniture/Beds|Furniture > Beds",
            "Furniture  /\tBeds / Twin|Furniture > Beds > Twin",
            "\" Furniture / Beds \"|\" Furniture > Beds \"",
            "Furniture /|\"Furniture > \"",
            "/ Beds|\" > Beds\"",
            "Furniture//Beds|Furniture >  > Beds",
            "Furniture|Furniture"
    })
    void collapsesWhitespaceAroundEverySlashOnly(String hierarchy, String expected) {
        String text = EmbeddingTextBuilder.build("bed", null, hierarchy, null);

        assertThat(text).isEqualTo("bed. " + expected);
    }
}
