package tn.oliveconnect.imports;

/** REFERENCE files arrive daily (varieties, mills, orchards...), DYNAMIC files every 15 minutes (weather, maturity...). */
public enum ImportSource {
    REFERENCE,
    DYNAMIC
}
