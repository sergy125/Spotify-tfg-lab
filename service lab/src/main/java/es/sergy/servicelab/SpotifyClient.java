package es.sergy.servicelab;

import java.util.List;

public interface SpotifyClient {
    List<SpotifyTrack> getTopTracks(TimeRange timeRange);
}
