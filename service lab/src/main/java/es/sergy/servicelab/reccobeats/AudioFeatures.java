package es.sergy.servicelab.reccobeats;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AudioFeatures(
        Double danceability, Double energy, Double valence, Double acousticness,
        Double instrumentalness, Double liveness, Double speechiness, Double tempo, Double loudness) {
    public boolean hasData() {
        return danceability != null || energy != null || valence != null || acousticness != null
                || instrumentalness != null || liveness != null || speechiness != null
                || tempo != null || loudness != null;
    }
}
