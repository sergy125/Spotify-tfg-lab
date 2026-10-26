package audiofeatures;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import models.Song;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class ReccoBeats {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();

    public Song addFeatures(Song song) throws IOException, InterruptedException {
        findFeatures(song);
        return song;
    }

    /** Indica si ReccoBeats ha completado las audiofeatures; los errores de red se propagan. */
    public boolean findFeatures(Song song) throws IOException, InterruptedException {
        String spotifyId = URLEncoder.encode(song.getId(), StandardCharsets.UTF_8);
        JsonObject response = get("/track?ids=" + spotifyId);
        if (response == null) return false;

        JsonArray tracks = response.getAsJsonArray("content");
        if (tracks.isEmpty()) return false;

        JsonObject track = tracks.get(0).getAsJsonObject();
        Integer popularity = getInteger(track, "popularity");
        if (popularity != null) song.setPopularidad(popularity);

        // Audio features require the ReccoBeats ID, not the Spotify ID.
        String reccoId = track.get("id").getAsString();
        JsonObject features = get("/track/" + reccoId + "/audio-features");
        if (features == null) return !song.hasMissingFeatures();

        song.setAcousticness(getDouble(features, "acousticness"));
        song.setDanceability(getDouble(features, "danceability"));
        song.setEnergy(getDouble(features, "energy"));
        song.setInstrumentalness(getDouble(features, "instrumentalness"));
        song.setKey(getInteger(features, "key"));
        song.setLiveness(getDouble(features, "liveness"));
        song.setLoudness(getDouble(features, "loudness"));
        song.setMode(getInteger(features, "mode"));
        song.setSpeechiness(getDouble(features, "speechiness"));
        song.setTempo(getDouble(features, "tempo"));
        song.setValence(getDouble(features, "valence"));
        if (!song.hasMissingFeatures()) song.setAi(false);
        return !song.hasMissingFeatures();
    }

    JsonObject get(String endpoint) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.reccobeats.com/v1" + endpoint))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) {
            throw new IOException("ReccoBeats request failed: HTTP " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private Double getDouble(JsonObject object, String field) {
        return !object.has(field) || object.get(field).isJsonNull()
                ? null : object.get(field).getAsDouble();
    }

    private Integer getInteger(JsonObject object, String field) {
        return !object.has(field) || object.get(field).isJsonNull()
                ? null : object.get(field).getAsInt();
    }
}
