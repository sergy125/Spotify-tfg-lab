package es.sergy.servicelab;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TrackImportService {
    private static final Logger log = LoggerFactory.getLogger(TrackImportService.class);
    private final SpotifyClient spotify;
    private final ReccoBeatsClient reccoBeats;
    private final TrackRepository repository;
    private final TransactionTemplate transaction;

    public TrackImportService(SpotifyClient spotify, ReccoBeatsClient reccoBeats,
            TrackRepository repository, PlatformTransactionManager transactionManager) {
        this.spotify = spotify;
        this.reccoBeats = reccoBeats;
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // Evita que dos importaciones del mismo proceso mezclen sus posiciones.
    public synchronized ImportSummary importTopTracks(TimeRange timeRange) {
        List<SpotifyTrack> topTracks = spotify.getTopTracks(timeRange);
        List<AudioFeatures> features = new ArrayList<>();
        boolean rateLimited = false;
        int withData = 0;
        for (SpotifyTrack track : topTracks) {
            AudioFeatures audio = null;
            if (!rateLimited) {
                try {
                    audio = reccoBeats.findFeatures(track.spotifyId()).orElse(null);
                } catch (ExternalApiException ex) {
                    log.warn("Sin audio para Spotify ID {}: {}", track.spotifyId(), ex.getMessage());
                    rateLimited = ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS;
                }
            }
            features.add(audio);
            if (audio != null) {
                withData++;
            }
        }

        // Las llamadas HTTP no mantienen abierta una transacción de base de datos.
        transaction.executeWithoutResult(status -> {
            repository.deleteAllInBatch();
            var imported = new ArrayList<Track>();
            for (int i = 0; i < topTracks.size(); i++) {
                SpotifyTrack data = topTracks.get(i);
                Track track = new Track(data.spotifyId());
                track.update(data, i + 1, features.get(i));
                imported.add(track);
            }
            repository.saveAllAndFlush(imported);
        });
        return new ImportSummary(topTracks.size(), withData, topTracks.size() - withData);
    }

    public record ImportSummary(int processed, int withReccoBeats, int withoutReccoBeats) { }
}
