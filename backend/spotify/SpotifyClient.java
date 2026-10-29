package spotify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches Spotify data as raw JSON strings using a supplied access token.
 */
public class SpotifyClient {
    private final String token;
    private final HttpClient client;

    public SpotifyClient(String token) {
        this.token = token;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public String getHistory() throws IOException, InterruptedException {
        return get("/me/player/recently-played?limit=50");
    }

    public String getTopLong() throws IOException, InterruptedException {
        return get("/me/top/tracks?time_range=long_term&limit=50");
    }

    public String getTopMedium() throws IOException, InterruptedException {
        return get("/me/top/tracks?time_range=medium_term&limit=50");
    }

    public String getTopShort() throws IOException, InterruptedException {
        return get("/me/top/tracks?time_range=short_term&limit=50");
    }

    public String getArtistTopShort() throws IOException, InterruptedException {
        return get("/me/top/artists?time_range=short_term&limit=50");
    }

    public String getArtistTopMedium() throws IOException, InterruptedException {
        return get("/me/top/artists?time_range=medium_term&limit=50");
    }

    public String getArtistTopLong() throws IOException, InterruptedException {
        return get("/me/top/artists?time_range=long_term&limit=50");
    }

    protected String get(String endpoint) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.spotify.com/v1" + endpoint))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Spotify request failed: HTTP " + response.statusCode()
                    + " - " + response.body());
        }
        return response.body();
    }
}
