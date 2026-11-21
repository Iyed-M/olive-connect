package tn.oliveconnect.imports;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import tn.oliveconnect.common.NotFoundException;
import tn.oliveconnect.common.PageResponse;

/** Reads of the import journal (GET /api/imports). */
@Service
public class ImportJournalService {

    private final MongoTemplate mongoTemplate;
    private final ImportJobRepository jobRepository;

    public ImportJournalService(MongoTemplate mongoTemplate, ImportJobRepository jobRepository) {
        this.mongoTemplate = mongoTemplate;
        this.jobRepository = jobRepository;
    }

    public PageResponse<ImportJobDto> list(String dataset, ImportStatus status, int page, int size) {
        Criteria filter = new Criteria();
        if (dataset != null && !dataset.isBlank()) {
            filter.and("dataset").is(dataset);
        }
        if (status != null) {
            filter.and("status").is(status);
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Query query = Query.query(filter);
        // the list does not need the (up to 500) rejected rows
        query.fields().exclude("errors");
        Page<ImportJob> jobs = PageableExecutionUtils.getPage(
                mongoTemplate.find(query.with(pageRequest), ImportJob.class), pageRequest,
                () -> mongoTemplate.count(Query.query(filter), ImportJob.class));
        return PageResponse.of(jobs, job -> ImportJobDto.from(job, false));
    }

    public ImportJobDto get(String id) {
        return ImportJobDto.from(jobRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Import job not found")), true);
    }
}
