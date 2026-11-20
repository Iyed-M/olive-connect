package tn.oliveconnect.imports;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.oliveconnect.common.FieldValidationException;
import tn.oliveconnect.config.AppProperties;

/**
 * The import pipeline of docs/07 §4: checksum, parse, validate, bulk write, journal, move the file.
 * The dataset-specific work is done by the DatasetImporter beans.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    static final int BATCH_SIZE = 1000;

    private final List<DatasetImporter<?>> importers;
    private final ImportJobRepository jobRepository;
    private final AutoAlertService autoAlertService;
    private final Path inbox;

    public ImportService(List<DatasetImporter<?>> importers, ImportJobRepository jobRepository,
            AutoAlertService autoAlertService, AppProperties properties) {
        this.importers = importers;
        this.jobRepository = jobRepository;
        this.autoAlertService = autoAlertService;
        this.inbox = Path.of(properties.imports().inboxDir());
    }

    /** Imports every file waiting in the inbox that belongs to the given source (one run at a time per instance). */
    public synchronized RunResult run(ImportSource source) {
        List<ImportJob> jobs = new ArrayList<>();
        int skipped = 0;
        for (Path file : listInbox()) {
            Optional<DatasetImporter<?>> importer = findImporter(file.getFileName().toString());
            if (importer.isPresent() && importer.get().source() != source) {
                continue;   // belongs to the other source, its own run will take it
            }
            Optional<ImportJob> job = importFile(file);
            if (job.isPresent()) {
                jobs.add(job.get());
            } else {
                skipped++;
            }
        }
        return new RunResult(jobs, skipped);
    }

    /** Stores an uploaded file in the inbox and imports it right away. */
    public synchronized RunResult upload(String originalName, InputStream content) {
        String fileName = originalName == null ? "" : Path.of(originalName).getFileName().toString();
        if (findImporter(fileName).isEmpty()) {
            throw new FieldValidationException("file", "File name '" + fileName
                    + "' does not match a known dataset (e.g. weather_202611210615.json)");
        }
        Path target = inbox.resolve(fileName);
        try {
            Files.createDirectories(inbox);
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Optional<ImportJob> job = importFile(target);
        return new RunResult(job.map(List::of).orElse(List.of()), job.isPresent() ? 0 : 1);
    }

    /** Imports one file and moves it. Empty result: the same file was already imported, so it is skipped. */
    synchronized Optional<ImportJob> importFile(Path file) {
        String fileName = file.getFileName().toString();
        String checksum = sha256(file);

        Optional<ImportJob> existing = jobRepository.findByChecksum(checksum);
        if (existing.isPresent() && Set.of(ImportStatus.SUCCESS, ImportStatus.PARTIAL).contains(existing.get().getStatus())) {
            moveTo(file, "processed");
            return Optional.empty();
        }

        // A file that FAILED before (or a crashed run) is retried on the same journal entry: checksum is unique
        Optional<DatasetImporter<?>> importer = findImporter(fileName);
        ImportJob job = existing.orElseGet(ImportJob::new);
        job.reset();
        job.setFileName(fileName);
        job.setChecksum(checksum);
        job.setDataset(importer.map(DatasetImporter::dataset).orElse("UNKNOWN"));
        job.setSource(importer.map(DatasetImporter::source).orElse(null));
        job.setStatus(ImportStatus.RUNNING);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);

        try {
            if (importer.isEmpty()) {
                throw new ImportFormatException("file name '" + fileName + "' does not match a known dataset");
            }
            process(importer.get(), file, job);
        } catch (ImportFormatException e) {
            fail(job, e.getMessage());
        } catch (Exception e) {
            log.error("Import of {} failed", fileName, e);
            fail(job, "INTERNAL: " + e.getMessage());
        }

        job.setFinishedAt(Instant.now());
        jobRepository.save(job);
        moveTo(file, job.getStatus() == ImportStatus.FAILED ? "rejected" : "processed");
        return Optional.of(job);
    }

    private <T> void process(DatasetImporter<T> importer, Path file, ImportJob job) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) {
            throw new ImportFormatException("file is larger than 20 MB");
        }
        checkUtf8(file);

        DatasetImporter.ParseResult<T> parsed = importer.parse(file);
        List<ImportError> errors = new ArrayList<>(parsed.errors());
        List<DatasetImporter.Row<T>> valid = new ArrayList<>();
        for (DatasetImporter.Row<T> row : parsed.rows()) {
            Optional<ImportError> error = importer.validate(row);
            if (error.isPresent()) {
                errors.add(error.get());
            } else {
                valid.add(row);
            }
        }
        List<ImportError> duplicates = importer.findDuplicates(valid);
        if (!duplicates.isEmpty()) {
            Set<Integer> duplicateLines = duplicates.stream().map(ImportError::line).collect(Collectors.toSet());
            valid.removeIf(row -> duplicateLines.contains(row.line()));
            errors.addAll(duplicates);
        }
        errors.sort(Comparator.comparingInt(ImportError::line));

        int total = parsed.rows().size() + parsed.errors().size();
        job.setTotalRows(total);
        job.setRejectedRows(errors.size());
        errors.forEach(job::addError);

        if (errors.size() * 2 > total) {   // more than 50 % rejected: commit nothing
            job.setStatus(ImportStatus.FAILED);
            return;
        }

        int inserted = 0;
        int updated = 0;
        for (int from = 0; from < valid.size(); from += BATCH_SIZE) {
            List<T> batch = valid.subList(from, Math.min(from + BATCH_SIZE, valid.size())).stream()
                    .map(DatasetImporter.Row::value).toList();
            DatasetImporter.PersistResult result = importer.persist(batch);
            inserted += result.inserted();
            updated += result.updated();
        }
        job.setInsertedRows(inserted);
        job.setUpdatedRows(updated);
        job.setStatus(errors.isEmpty() ? ImportStatus.SUCCESS : ImportStatus.PARTIAL);

        if (importer.source() == ImportSource.DYNAMIC) {
            autoAlertService.evaluate();
        }
    }

    private void fail(ImportJob job, String reason) {
        job.addError(new ImportError(0, "file", reason));
        job.setStatus(ImportStatus.FAILED);
    }

    private Optional<DatasetImporter<?>> findImporter(String fileName) {
        return importers.stream().filter(importer -> importer.accepts(fileName)).findFirst();
    }

    private List<Path> listInbox() {
        if (!Files.isDirectory(inbox)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(inbox)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void moveTo(Path file, String folder) {
        try {
            Path directory = Files.createDirectories(inbox.resolve(folder));
            Files.move(file, directory.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Could not move {} to {}", file, folder, e);
        }
    }

    private static String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The decoder of an InputStreamReader replaces bad bytes silently, so we ask it to report them. */
    private static void checkUtf8(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
                Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8.newDecoder())) {
            char[] buffer = new char[8192];
            while (reader.read(buffer) != -1) {
                // reading is enough: a malformed byte throws
            }
        } catch (CharacterCodingException e) {
            throw new ImportFormatException("file is not valid UTF-8");
        }
    }
}
