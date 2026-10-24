package com.avanti.recengine.recommender.adapter.out.clickstream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the synthetic clickstream CSV (event_id,user_id,session_id,
 * timestamp,query_id,query,position,product_id,event_type,dwell_time_ms)
 * in file order.
 */
public final class ClickstreamCsvReader {

    private static final CSVFormat FORMAT = CSVFormat.Builder.create(CSVFormat.DEFAULT)
            .setHeader()
            .setSkipHeaderRecord(true)
            .build();

    private ClickstreamCsvReader() {
    }

    public static List<ClickstreamEvent> read(Path clickstreamCsv) {
        List<ClickstreamEvent> events = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(clickstreamCsv, StandardCharsets.UTF_8);
             CSVParser parser = FORMAT.parse(reader)) {
            for (CSVRecord row : parser) {
                events.add(toEvent(row));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + clickstreamCsv, e);
        }
        return events;
    }

    private static ClickstreamEvent toEvent(CSVRecord row) {
        return new ClickstreamEvent(
                row.get("session_id"),
                row.get("user_id"),
                OffsetDateTime.parse(row.get("timestamp")),
                row.get("query_id"),
                Integer.parseInt(row.get("position")),
                row.get("product_id"),
                row.get("event_type")
        );
    }
}
