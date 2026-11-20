package tn.oliveconnect.orchard;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/varieties")
public class OliveVarietyController {

    private final OrchardService orchardService;

    public OliveVarietyController(OrchardService orchardService) {
        this.orchardService = orchardService;
    }

    @GetMapping
    public List<OliveVarietyDto> list() {
        return orchardService.listVarieties();
    }
}
