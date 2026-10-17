package es.sergy.servicelab.reccobeats;

import es.sergy.servicelab.errors.ExternalApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ReccoBeatsClient {
    private final RestClient client;

    public ReccoBeatsClient(@Qualifier("reccoBeatsRestClient") RestClient client) {
        this.client = client;
    }

    public Optional<AudioFeatures> findFeatures(String spotifyId) {
        try {
            // El endpoint de audio requiere el UUID de ReccoBeats, no el ID de Spotify.
            Tracks response = client.get().uri(uri -> uri.path("/track")
                    .queryParam("ids", spotifyId).build()).retrieve().body(Tracks.class);
            if (response == null || response.content() == null) {
                throw new ExternalApiException(HttpStatus.BAD_GATEWAY, "Respuesta de ReccoBeats no válida.");
            }
            if (response.content().isEmpty()) {
                return Optional.empty();
            }
            ReccoTrack match = response.content().getFirst();
            if (response.content().size() != 1 || match == null || match.id() == null || match.id().isBlank()) {
                throw new ExternalApiException(HttpStatus.BAD_GATEWAY, "Identificador de ReccoBeats no válido.");
            }
            AudioFeatures features = client.get().uri("/track/{id}/audio-features", match.id())
                    .retrieve().body(AudioFeatures.class);
            return Optional.ofNullable(features).filter(AudioFeatures::hasData);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404) {
                return Optional.empty();
            }
            if (status == 429) {
                throw new ExternalApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Límite de peticiones de ReccoBeats.");
            }
            throw new ExternalApiException(HttpStatus.BAD_GATEWAY, "ReccoBeats respondió HTTP " + status + ".");
        } catch (RestClientException ex) {
            throw new ExternalApiException(HttpStatus.BAD_GATEWAY,
                    "No se pudo conectar con ReccoBeats o leer su respuesta.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Tracks(List<ReccoTrack> content) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReccoTrack(String id) { }
}
