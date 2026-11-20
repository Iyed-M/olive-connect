package tn.oliveconnect.imports;

public enum ImportStatus {
    RUNNING,
    SUCCESS,   // no rejected record
    PARTIAL,   // some records rejected, the others are committed
    FAILED     // unusable file, or more than 50 % rejected: nothing is committed
}
