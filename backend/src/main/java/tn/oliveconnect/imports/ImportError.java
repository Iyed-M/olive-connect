package tn.oliveconnect.imports;

/** One rejected record (docs/07 §3). For JSON files, line is the 1-based position of the record in the array. */
public record ImportError(int line, String field, String reason) {
}
