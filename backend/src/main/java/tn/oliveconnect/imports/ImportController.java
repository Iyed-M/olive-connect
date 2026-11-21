package tn.oliveconnect.imports;

import java.io.IOException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tn.oliveconnect.common.PageResponse;

@RestController
@RequestMapping("/api/imports")
@PreAuthorize("hasRole('ADMIN')")
public class ImportController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ImportService importService;
    private final ImportJournalService journalService;

    public ImportController(ImportService importService, ImportJournalService journalService) {
        this.importService = importService;
        this.journalService = journalService;
    }

    @GetMapping
    public PageResponse<ImportJobDto> list(@RequestParam(required = false) String dataset,
            @RequestParam(required = false) ImportStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return journalService.list(dataset, status, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    @GetMapping("/{id}")
    public ImportJobDto get(@PathVariable String id) {
        return journalService.get(id);
    }

    /** Imports the files waiting in the inbox now, without waiting for the scheduler. */
    @PostMapping("/run")
    public RunResultDto run(@RequestParam("type") ImportSource type) {
        return RunResultDto.from(importService.run(type));
    }

    @PostMapping("/upload")
    public RunResultDto upload(@RequestParam("file") MultipartFile file) throws IOException {
        return RunResultDto.from(importService.upload(file.getOriginalFilename(), file.getInputStream()));
    }
}
