package es.sergy.servicelab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "tracks")
@Check(constraints = "rank is null or (rank >= 1 and rank <= 50)")
public class Track {
    @Id
    @Column(name = "spotify_id", nullable = false, length = 64)
    private String spotifyId;
    @Column(nullable = false, columnDefinition = "text")
    private String name;
    @Column(nullable = false, columnDefinition = "text")
    private String artists;
    @Column(nullable = false, columnDefinition = "text")
    private String album;
    @Column(nullable = false)
    private int durationMs;
    private Integer rank;
    private Double danceability;
    private Double energy;
    private Double valence;
    private Double acousticness;
    private Double instrumentalness;
    private Double liveness;
    private Double speechiness;
    private Double tempo;
    private Double loudness;

    protected Track() { }

    public Track(String spotifyId) {
        this.spotifyId = spotifyId;
    }

    public void update(SpotifyTrack data, int rank, AudioFeatures audio) {
        this.name = data.name();
        this.artists = data.artists();
        this.album = data.album();
        this.durationMs = data.durationMs();
        this.rank = rank;
        this.danceability = audio == null ? null : audio.danceability();
        this.energy = audio == null ? null : audio.energy();
        this.valence = audio == null ? null : audio.valence();
        this.acousticness = audio == null ? null : audio.acousticness();
        this.instrumentalness = audio == null ? null : audio.instrumentalness();
        this.liveness = audio == null ? null : audio.liveness();
        this.speechiness = audio == null ? null : audio.speechiness();
        this.tempo = audio == null ? null : audio.tempo();
        this.loudness = audio == null ? null : audio.loudness();
    }

    public String getSpotifyId() {
        return spotifyId;
    }

    public String getName() {
        return name;
    }

    public String getArtists() {
        return artists;
    }

    public String getAlbum() {
        return album;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public Integer getRank() {
        return rank;
    }

    public Double getDanceability() {
        return danceability;
    }

    public Double getEnergy() {
        return energy;
    }

    public Double getValence() {
        return valence;
    }

    public Double getAcousticness() {
        return acousticness;
    }

    public Double getInstrumentalness() {
        return instrumentalness;
    }

    public Double getLiveness() {
        return liveness;
    }

    public Double getSpeechiness() {
        return speechiness;
    }

    public Double getTempo() {
        return tempo;
    }

    public Double getLoudness() {
        return loudness;
    }
}
