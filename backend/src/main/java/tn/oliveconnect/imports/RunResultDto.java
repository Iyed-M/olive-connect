package tn.oliveconnect.imports;

import java.util.List;

/** Response of POST /api/imports/run and /upload. */
public record RunResultDto(List<ImportJobDto> jobs, int skippedFiles) {

    static RunResultDto from(RunResult result) {
        return new RunResultDto(result.jobs().stream().map(job -> ImportJobDto.from(job, true)).toList(),
                result.skippedFiles());
    }
}
