package tn.oliveconnect.imports;

import java.util.List;

/** Outcome of an import run: the journal entries it created, and the files skipped because already imported. */
public record RunResult(List<ImportJob> jobs, int skippedFiles) {
}
