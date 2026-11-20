package tn.oliveconnect.orchard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrchardTest {

    @Test
    void recomputeTotalsUsesPlotAreaAndTreeCountAndChoosesLargestPlotVariety() {
        Plot first = plot("P1", "CHEMLALI", 4.5, 450);
        Plot second = plot("P2", "CHETOUI", 1.2, 180);
        Orchard orchard = new Orchard();
        orchard.setPlots(List.of(first, second));

        orchard.recomputeTotals();

        assertThat(orchard.getAreaHa()).isEqualTo(5.7);
        assertThat(orchard.getTreeCount()).isEqualTo(630);
        assertThat(orchard.getMainVarietyCode()).isEqualTo("CHEMLALI");
    }

    private Plot plot(String code, String variety, double area, int trees) {
        Plot plot = new Plot();
        plot.setCode(code);
        plot.setVarietyCode(variety);
        plot.setAreaHa(area);
        plot.setTreeCount(trees);
        return plot;
    }
}
