package tn.oliveconnect.round;

import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planning")
@PreAuthorize("hasRole('COLLECTION_MANAGER')")
public class PlanningController {

    private final PlanningService planningService;

    public PlanningController(PlanningService planningService) {
        this.planningService = planningService;
    }

    @GetMapping("/suggestions")
    public PlanningSuggestionsDto suggestions(@RequestParam(required = false) LocalDate date,
                                              Authentication authentication) {
        return planningService.suggestions(date, authentication);
    }
}
