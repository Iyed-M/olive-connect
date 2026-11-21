package tn.oliveconnect.imports;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads the ';'-separated CSV files of docs/07 §2 (UTF-8, header required, no quoting). */
final class CsvFile {

    /** A data line: its number in the file (the header is line 1) and its cells by column name. */
    record Line(int number, Map<String, String> values) {
    }

    record Content(List<Line> lines, List<ImportError> errors) {
    }

    private CsvFile() {
    }

    /** Throws ImportFormatException when the header is missing or lacks a required column. */
    static Content read(Path file, List<String> requiredColumns) throws IOException {
        List<Line> lines = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new ImportFormatException("header is missing");
            }
            String[] header = headerLine.replace("﻿", "").split(";", -1);
            List<String> columns = new ArrayList<>();
            for (String name : header) {
                columns.add(name.trim());
            }
            for (String required : requiredColumns) {
                if (!columns.contains(required)) {
                    throw new ImportFormatException("missing column '" + required + "'");
                }
            }
            int number = 1;
            String text;
            while ((text = reader.readLine()) != null) {
                number++;
                if (text.isBlank()) {
                    continue;
                }
                String[] cells = text.split(";", -1);
                if (cells.length != columns.size()) {
                    errors.add(new ImportError(number, "row",
                            "expected " + columns.size() + " columns, found " + cells.length));
                    continue;
                }
                Map<String, String> values = new HashMap<>();
                for (int i = 0; i < cells.length; i++) {
                    values.put(columns.get(i), cells[i]);
                }
                lines.add(new Line(number, values));
            }
        }
        return new Content(lines, errors);
    }
}
