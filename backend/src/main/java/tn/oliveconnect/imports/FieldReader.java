package tn.oliveconnect.imports;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.function.Function;

/**
 * Reads the typed fields of one record (a CSV line or a JSON object) and remembers the first problem,
 * so an importer can read every field and then ask {@link #error()} once.
 */
final class FieldReader {

    private final int line;
    private final Function<String, String> raw;
    private ImportError error;

    FieldReader(int line, Function<String, String> raw) {
        this.line = line;
        this.raw = raw;
    }

    static FieldReader of(int position, JsonNode object) {
        return new FieldReader(position, field -> {
            JsonNode value = object.get(field);
            return value == null || value.isNull() || value.isContainerNode() ? null : value.asText();
        });
    }

    ImportError error() {
        return error;
    }

    String text(String field) {
        String value = optionalText(field);
        if (value == null) {
            fail(field, "is missing");
        }
        return value;
    }

    /** Null when absent or blank. */
    String optionalText(String field) {
        String value = raw.apply(field);
        return value == null || value.isBlank() ? null : value.trim();
    }

    double decimal(String field) {
        String value = text(field);
        return value == null ? 0 : toDecimal(field, value);
    }

    /** Blank means 0. */
    double optionalDecimal(String field) {
        String value = optionalText(field);
        return value == null ? 0 : toDecimal(field, value);
    }

    int integer(String field) {
        String value = text(field);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            fail(field, "'" + value + "' is not a whole number");
            return 0;
        }
    }

    boolean bool(String field) {
        String value = text(field);
        if (value == null) {
            return false;
        }
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            fail(field, "'" + value + "' is not true or false");
        }
        return Boolean.parseBoolean(value);
    }

    Instant instant(String field) {
        String value = text(field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            fail(field, "'" + value + "' is not an ISO-8601 date");
            return null;
        }
    }

    private double toDecimal(String field, String value) {
        try {
            double number = Double.parseDouble(value);
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException e) {
            fail(field, "'" + value + "' is not a number");
            return 0;
        }
    }

    private void fail(String field, String reason) {
        if (error == null) {
            error = new ImportError(line, field, reason);
        }
    }
}
