package tn.oliveconnect.map;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public map (FR-4.1, 4.2, 4.4): open to everybody, anonymous data only. */
@RestController
@RequestMapping("/api/map")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    /** Returns the cached GeoJSON text as is, so a cache hit does not parse and rebuild it. */
    @GetMapping(value = "/public", produces = "application/geo+json")
    public ResponseEntity<String> publicMap(
            @RequestParam String bbox,
            @RequestParam(required = false) Integer zoom,
            @RequestParam(required = false) String variety,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double maturityMin,
            @RequestParam(required = false) Double maturityMax) {
        String json = mapService.publicMap(bbox, zoom, variety, status, maturityMin, maturityMax);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic()).body(json);
    }
}
