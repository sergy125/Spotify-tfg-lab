package es.sergy.servicelab;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ImportController {
    private final TrackImportService service;
    private final TrackRepository repository;

    public ImportController(TrackImportService service, TrackRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping("/import/top-tracks")
    public TrackImportService.ImportSummary importTopTracks(
            @RequestParam(name = "time_range", defaultValue = "medium_term") String timeRange) {
        TimeRange range;
        try {
            range = TimeRange.fromApiValue(timeRange);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        return service.importTopTracks(range);
    }

    @GetMapping("/tracks")
    public List<Track> tracks() {
        return repository.findAllOrderedByRank();
    }
}
