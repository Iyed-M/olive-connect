package tn.oliveconnect.mill;

/** Quality measurements embedded in the weighing fact. */
public class QualityAssessment {

    private QualityGrade grade;
    private double defectRatePct;
    private double leafRatePct;
    private double estimatedOilYieldPct;

    public QualityGrade getGrade() { return grade; }
    public void setGrade(QualityGrade grade) { this.grade = grade; }
    public double getDefectRatePct() { return defectRatePct; }
    public void setDefectRatePct(double defectRatePct) { this.defectRatePct = defectRatePct; }
    public double getLeafRatePct() { return leafRatePct; }
    public void setLeafRatePct(double leafRatePct) { this.leafRatePct = leafRatePct; }
    public double getEstimatedOilYieldPct() { return estimatedOilYieldPct; }
    public void setEstimatedOilYieldPct(double estimatedOilYieldPct) {
        this.estimatedOilYieldPct = estimatedOilYieldPct;
    }
}
