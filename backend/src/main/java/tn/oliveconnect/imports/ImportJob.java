package tn.oliveconnect.imports;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Journal entry of one imported file. The checksum is unique, so the same file is never imported twice. */
@Document("import_jobs")
@CompoundIndex(name = "dataset_status", def = "{ 'dataset': 1, 'status': 1 }")
public class ImportJob {

    /** errors[] is bounded (R2); errorCount keeps the real total. */
    public static final int MAX_STORED_ERRORS = 500;

    @Id
    private String id;
    private ImportSource source;
    private String dataset;
    private String fileName;
    @Indexed(unique = true)
    private String checksum;
    private ImportStatus status;
    private int totalRows;
    private int insertedRows;
    private int updatedRows;
    private int rejectedRows;
    private int errorCount;
    private List<ImportError> errors = new ArrayList<>();
    @Indexed(name = "started_at_desc", direction = IndexDirection.DESCENDING)
    private Instant startedAt;
    private Instant finishedAt;

    public void addError(ImportError error) {
        errorCount++;
        if (errors.size() < MAX_STORED_ERRORS) {
            errors.add(error);
        }
    }

    /** Puts the job back to its initial state (a FAILED file that is submitted again reuses its journal entry). */
    public void reset() {
        totalRows = 0;
        insertedRows = 0;
        updatedRows = 0;
        rejectedRows = 0;
        errorCount = 0;
        errors = new ArrayList<>();
        finishedAt = null;
    }

    public String getId() {
        return id;
    }

    public ImportSource getSource() {
        return source;
    }

    public void setSource(ImportSource source) {
        this.source = source;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public void setStatus(ImportStatus status) {
        this.status = status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getInsertedRows() {
        return insertedRows;
    }

    public void setInsertedRows(int insertedRows) {
        this.insertedRows = insertedRows;
    }

    public int getUpdatedRows() {
        return updatedRows;
    }

    public void setUpdatedRows(int updatedRows) {
        this.updatedRows = updatedRows;
    }

    public int getRejectedRows() {
        return rejectedRows;
    }

    public void setRejectedRows(int rejectedRows) {
        this.rejectedRows = rejectedRows;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public List<ImportError> getErrors() {
        return errors;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
