package tn.oliveconnect.imports;

import java.time.Instant;
import java.util.List;

/** A journal entry. errors holds at most 500 rejected rows (the detail view); errorCount is the real total. */
public record ImportJobDto(
        String id,
        ImportSource source,
        String dataset,
        String fileName,
        ImportStatus status,
        int totalRows,
        int insertedRows,
        int updatedRows,
        int rejectedRows,
        int errorCount,
        List<ImportError> errors,
        Instant startedAt,
        Instant finishedAt) {

    static ImportJobDto from(ImportJob job, boolean withErrors) {
        return new ImportJobDto(job.getId(), job.getSource(), job.getDataset(), job.getFileName(), job.getStatus(),
                job.getTotalRows(), job.getInsertedRows(), job.getUpdatedRows(), job.getRejectedRows(),
                job.getErrorCount(), withErrors ? job.getErrors() : List.of(), job.getStartedAt(), job.getFinishedAt());
    }
}
