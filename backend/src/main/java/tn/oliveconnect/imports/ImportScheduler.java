package tn.oliveconnect.imports;

import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the imports (docs/07 §4). ShedLock keeps the lock in MongoDB, so when the API runs
 * on several instances only one of them executes a given run.
 */
@Component
public class ImportScheduler {

    private final ImportService importService;

    public ImportScheduler(ImportService importService) {
        this.importService = importService;
    }

    /** Reference data, every day at 02:00. */
    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "referenceImport", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void importReferenceData() {
        importService.run(ImportSource.REFERENCE);
    }

    /** Dynamic data, 15 minutes after the previous run ended (first run 15 minutes after startup). */
    @Scheduled(fixedDelay = 15, initialDelay = 15, timeUnit = TimeUnit.MINUTES)
    @SchedulerLock(name = "dynamicImport", lockAtMostFor = "PT14M", lockAtLeastFor = "PT1M")
    public void importDynamicData() {
        importService.run(ImportSource.DYNAMIC);
    }
}
