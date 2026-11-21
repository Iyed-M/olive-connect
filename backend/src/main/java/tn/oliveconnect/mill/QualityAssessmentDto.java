package tn.oliveconnect.mill;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record QualityAssessmentDto(
        @NotNull QualityGrade grade,
        @DecimalMin("0.0") @DecimalMax("100.0") double defectRatePct,
        @DecimalMin("0.0") @DecimalMax("100.0") double leafRatePct,
        @DecimalMin("0.0") @DecimalMax("100.0") double estimatedOilYieldPct) {

    public QualityAssessment toEntity() {
        QualityAssessment assessment = new QualityAssessment();
        assessment.setGrade(grade);
        assessment.setDefectRatePct(defectRatePct);
        assessment.setLeafRatePct(leafRatePct);
        assessment.setEstimatedOilYieldPct(estimatedOilYieldPct);
        return assessment;
    }

    public static QualityAssessmentDto from(QualityAssessment assessment) {
        return assessment == null ? null : new QualityAssessmentDto(assessment.getGrade(),
                assessment.getDefectRatePct(), assessment.getLeafRatePct(), assessment.getEstimatedOilYieldPct());
    }
}
