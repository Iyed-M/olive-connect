package tn.oliveconnect.imports;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads the records of a JSON file shaped like { "...": ..., "<arrayName>": [ {...}, {...} ] }. */
final class JsonArrayFile {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** One element of the array; position is 1-based (it is the "line" of the journal). */
    record Item(int position, JsonNode node) {
    }

    private JsonArrayFile() {
    }

    /** Throws ImportFormatException when the file is not JSON or has no such array. */
    static List<Item> read(Path file, String arrayName) throws IOException {
        List<Item> items = new ArrayList<>();
        boolean found = false;
        try (JsonParser parser = JSON.createParser(file.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new ImportFormatException("the file must contain a JSON object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                JsonToken value = parser.nextToken();
                if (!arrayName.equals(name)) {
                    parser.skipChildren();
                    continue;
                }
                if (value != JsonToken.START_ARRAY) {
                    throw new ImportFormatException("'" + arrayName + "' must be an array");
                }
                found = true;
                int position = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    position++;
                    items.add(new Item(position, JSON.readTree(parser)));
                }
            }
        } catch (JsonProcessingException e) {
            throw new ImportFormatException("invalid JSON: " + e.getOriginalMessage());
        }
        if (!found) {
            throw new ImportFormatException("missing '" + arrayName + "' array");
        }
        return items;
    }
}
