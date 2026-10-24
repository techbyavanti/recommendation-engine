package com.avanti.recengine.support.wands;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads WANDS {@code product.csv}. Despite the .csv extension, the file is
 * tab-delimited with unquoted fields (descriptions contain literal commas),
 * and the category column is literally named {@code category hierarchy}
 * (space, not underscore) — both ported from the sibling {@code search}
 * project's {@code WandsCsvLoader}, which discovered these by inspecting the
 * raw bytes rather than assuming a comma-delimited format.
 */
public final class WandsProductCsvLoader {

    private static final CSVFormat WANDS_FORMAT = CSVFormat.Builder.create(CSVFormat.TDF)
            .setHeader()
            .setSkipHeaderRecord(true)
            .setQuote(null)
            .build();

    private WandsProductCsvLoader() {
    }

    public static List<WandsProductRow> load(Path productCsv) {
        List<WandsProductRow> products = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(productCsv, StandardCharsets.UTF_8);
             CSVParser parser = WANDS_FORMAT.parse(reader)) {
            for (CSVRecord row : parser) {
                products.add(new WandsProductRow(
                        row.get("product_id"),
                        row.get("product_name"),
                        blankToNull(row.get("product_class")),
                        blankToNull(row.get("category hierarchy")),
                        blankToNull(row.get("product_description")),
                        row.get("product_features"),
                        parseInt(row.get("rating_count")),
                        parseDouble(row.get("average_rating")),
                        parseInt(row.get("review_count"))
                ));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + productCsv, e);
        }
        return products;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    /** Rating fields are stored as floats (e.g. "15.0") even though they're conceptually counts. */
    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (int) Double.parseDouble(value);
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Double.parseDouble(value);
    }
}
