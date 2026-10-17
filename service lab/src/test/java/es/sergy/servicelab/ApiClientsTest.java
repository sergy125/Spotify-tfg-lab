package es.sergy.servicelab;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ApiClientsTest {
    private MockRestServiceServer server;
    private RestClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl("https://example.test/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
    }

    @ParameterizedTest
    @EnumSource(TimeRange.class)
    void spotifyUsesExactQueryAndPreservesOrderAndAllArtists(TimeRange timeRange) {
        server.expect(requestTo("https://example.test/v1/me/top/tracks?time_range=" + timeRange.apiValue() + "&limit=50"))
                .andExpect(header("Authorization", "Bearer manual-token"))
                .andRespond(withSuccess("""
                        {"items":[
                          {"id":"b","name":"Second","artists":[{"name":"A"},{"name":"B"}],
                           "album":{"name":"Album"},"duration_ms":123,"ignored":true},
                          {"id":"a","name":"First","artists":[{"name":"C"}],
                           "album":{"name":"Other"},"duration_ms":456}]}
                        """, MediaType.APPLICATION_JSON));
        var tracks = new SpotifyTokenClient(client, "manual-token").getTopTracks(timeRange);
        assertThat(tracks).extracting(SpotifyTrack::spotifyId).containsExactly("b", "a");
        assertThat(tracks.getFirst().artists()).isEqualTo("A, B");
        assertThat(tracks.getFirst().durationMs()).isEqualTo(123);
        server.verify();
    }

    @Test
    void expiredTokenHasSafeActionableError() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("SECRET UPSTREAM BODY"));
        assertThatThrownBy(() -> new SpotifyTokenClient(client, "secret-token").getTopTracks(TimeRange.MEDIUM_TERM))
                .isInstanceOfSatisfying(ExternalApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getMessage()).contains("expirado").doesNotContain("secret", "SECRET");
                });
        server.verify();
    }

    @Test
    void spotifyRateLimitPreservesRetryAfter() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "30"));
        assertThatThrownBy(() -> new SpotifyTokenClient(client, "token").getTopTracks(TimeRange.MEDIUM_TERM))
                .isInstanceOfSatisfying(ExternalApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getRetryAfter()).isEqualTo("30");
                });
        server.verify();
    }

    @Test
    void missingTokenDoesNotSendRequest() {
        assertThatThrownBy(() -> new SpotifyTokenClient(client, " ").getTopTracks(TimeRange.MEDIUM_TERM))
                .isInstanceOfSatisfying(ExternalApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }

    @Test
    void invalidSpotifyResponseIsNotTreatedAsAnEmptyRanking() {
        server.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new SpotifyTokenClient(client, "token").getTopTracks(TimeRange.MEDIUM_TERM))
                .isInstanceOfSatisfying(ExternalApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    @Test
    void reccoResolvesSpotifyIdBeforeRequestingAudio() {
        server.expect(requestTo("https://example.test/v1/track?ids=spotify-id"))
                .andRespond(withSuccess("{\"content\":[{\"id\":\"recco-uuid\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/v1/track/recco-uuid/audio-features"))
                .andRespond(withSuccess("""
                        {"id":"recco-uuid","danceability":0.8,"energy":0.9,"valence":0.6,
                         "acousticness":0.1,"instrumentalness":0.2,"liveness":0.3,"speechiness":0.4,
                         "tempo":120.5,"loudness":-7.2}
                        """, MediaType.APPLICATION_JSON));
        var audio = new ReccoBeatsClient(client).findFeatures("spotify-id").orElseThrow();
        assertThat(audio).isEqualTo(new AudioFeatures(.8, .9, .6, .1, .2, .3, .4, 120.5, -7.2));
        server.verify();
    }

    @Test
    void emptyLookupDoesNotRequestAudio() {
        server.expect(anything()).andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));
        assertThat(new ReccoBeatsClient(client).findFeatures("missing")).isEmpty();
        server.verify();
    }

    @Test
    void missingAudioIsOptional() {
        server.expect(anything()).andRespond(withSuccess("{\"content\":[{\"id\":\"uuid\"}]}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(new ReccoBeatsClient(client).findFeatures("missing")).isEmpty();
        server.verify();
    }

    @Test
    void reccoServerFailureIsDistinguishedFromNotFound() {
        server.expect(anything()).andRespond(withServerError());
        assertThatThrownBy(() -> new ReccoBeatsClient(client).findFeatures("id"))
                .isInstanceOfSatisfying(ExternalApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        server.verify();
    }

    @Test
    void reccoRateLimitCanStopFurtherEnrichmentRequests() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> new ReccoBeatsClient(client).findFeatures("id"))
                .isInstanceOfSatisfying(ExternalApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        server.verify();
    }
}
