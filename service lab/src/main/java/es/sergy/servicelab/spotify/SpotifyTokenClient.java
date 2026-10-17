package es.sergy.servicelab.spotify;

import es.sergy.servicelab.errors.ExternalApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class SpotifyTokenClient implements SpotifyClient {
    private final RestClient client;
    private final String token;

    public SpotifyTokenClient(@Qualifier("spotifyRestClient") RestClient client,
            @Value("${spotify.access-token}") String token) {
        this.client = client;
        this.token = token.trim();
    }

    @Override
    public List<SpotifyTrack> getTopTracks(TimeRange timeRange) {
        if (token.isBlank()) {
            throw new ExternalApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configura SPOTIFY_ACCESS_TOKEN y reinicia el servicio.");
        }
        try {
            TopTracks response = client.get()
                    .uri(uri -> uri.path("/me/top/tracks")
                            .queryParam("time_range", timeRange.apiValue()).queryParam("limit", 50).build())
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(TopTracks.class);
            if (response == null || response.items() == null || response.items().size() > 50) {
                throw invalidResponse();
            }
            var ids = new HashSet<String>();
            for (Item item : response.items()) {
                if (item == null || item.id() == null || item.id().isBlank() || !ids.add(item.id())
                        || item.name() == null || item.artists() == null || item.album() == null
                        || item.album().name() == null || item.durationMs() == null || item.durationMs() < 0
                        || item.artists().stream().anyMatch(a -> a == null || a.name() == null)) {
                    throw invalidResponse();
                }
            }
            return response.items().stream().map(item -> new SpotifyTrack(item.id(), item.name(),
                    item.artists().stream().map(Artist::name).collect(Collectors.joining(", ")),
                    item.album().name(), item.durationMs())).toList();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401) {
                throw new ExternalApiException(HttpStatus.UNAUTHORIZED,
                        "El token de Spotify ha expirado o no es válido. Sustitúyelo y reinicia el servicio.");
            }
            if (status == 403) {
                throw new ExternalApiException(HttpStatus.FORBIDDEN,
                        "Spotify ha denegado el acceso. Comprueba el permiso user-top-read y el acceso de tu usuario a la app.");
            }
            if (status == 429) {
                String retryAfter = ex.getResponseHeaders() == null ? null
                        : ex.getResponseHeaders().getFirst("Retry-After");
                throw new ExternalApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Límite de peticiones de Spotify. Espera antes de volver a importar.", retryAfter);
            }
            throw new ExternalApiException(HttpStatus.BAD_GATEWAY, "Spotify respondió HTTP " + status + ".");
        } catch (RestClientException ex) {
            throw new ExternalApiException(HttpStatus.BAD_GATEWAY,
                    "No se pudo leer la respuesta de Spotify. Comprueba la conexión e inténtalo de nuevo.");
        }
    }

    private ExternalApiException invalidResponse() {
        return new ExternalApiException(HttpStatus.BAD_GATEWAY, "Spotify devolvió un ranking no válido.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TopTracks(List<Item> items) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String id, String name, List<Artist> artists, Album album,
                @JsonProperty("duration_ms") Integer durationMs) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Artist(String name) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Album(String name) { }
}
