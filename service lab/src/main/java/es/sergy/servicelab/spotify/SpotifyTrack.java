package es.sergy.servicelab.spotify;

public record SpotifyTrack(String spotifyId, String name, String artists, String album, int durationMs) {
}
