package es.sergy.servicelab.spotify;

import java.util.List;

public interface SpotifyClient {
    List<SpotifyTrack> getTopTracks(TimeRange timeRange);
}
