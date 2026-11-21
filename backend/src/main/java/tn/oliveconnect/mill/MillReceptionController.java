package tn.oliveconnect.mill;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/receptions")
@PreAuthorize("hasAnyRole('FARMER', 'MILL_MANAGER', 'COLLECTION_MANAGER', 'ADMIN')")
public class MillReceptionController {

    private final MillReceptionService millReceptionService;

    public MillReceptionController(MillReceptionService millReceptionService) {
        this.millReceptionService = millReceptionService;
    }

    @GetMapping
    public List<MillReceptionDto> list(@RequestParam(required = false) Boolean mine,
                                       Authentication authentication) {
        return millReceptionService.list(mine, authentication);
    }

    @GetMapping("/{id}")
    public MillReceptionDto get(@PathVariable String id, Authentication authentication) {
        return millReceptionService.get(id, authentication);
    }
}
