package es.sergy.servicelab.imports;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import es.sergy.servicelab.errors.ExternalApiException;
import es.sergy.servicelab.reccobeats.AudioFeatures;
import es.sergy.servicelab.reccobeats.ReccoBeatsClient;
import es.sergy.servicelab.spotify.SpotifyClient;
import es.sergy.servicelab.spotify.SpotifyTrack;
import es.sergy.servicelab.spotify.TimeRange;
import es.sergy.servicelab.tracks.Track;
import es.sergy.servicelab.tracks.TrackRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "import.on-startup=false",
        "spring.jpa.hibernate.ddl-auto=create-drop" })
@AutoConfigureMockMvc
class TrackImportIntegrationTest {
    @TempDir
    static Path databaseDirectory;

    @DynamicPropertySource
    static void sqliteDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseDirectory.resolve("test.db"));
    }

    @Autowired TrackRepository repository;
    @Autowired TrackImportService service;
    @Autowired MockMvc mvc;
    @MockitoBean SpotifyClient spotify;
    @MockitoBean ReccoBeatsClient recco;

    private static final AudioFeatures AUDIO = new AudioFeatures(.8, .7, .6, .5, .4, .3, .2, 121., -6.);

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    private SpotifyTrack track(String id) {
        return new SpotifyTrack(id, "Song " + id, "Artist A, Artist B", "Album", 180000);
    }

    @Test
    void importsMissingAudioAndContinuesAfterASongFails() throws Exception {
        when(spotify.getTopTracks(any(TimeRange.class))).thenReturn(List.of(track("b"), track("a"), track("c")));
        when(recco.findFeatures("b")).thenReturn(Optional.of(AUDIO));
        when(recco.findFeatures("a")).thenThrow(new ExternalApiException(HttpStatus.BAD_GATEWAY, "Error de red"));
        when(recco.findFeatures("c")).thenReturn(Optional.empty());
        mvc.perform(post("/api/import/top-tracks"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(3))
                .andExpect(jsonPath("$.withReccoBeats").value(1))
                .andExpect(jsonPath("$.withoutReccoBeats").value(2));
        mvc.perform(get("/api/tracks"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].spotifyId").value("b"))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].danceability").value(.8))
                .andExpect(jsonPath("$[1].spotifyId").value("a"))
                .andExpect(jsonPath("$[2].rank").value(3));
        assertThat(repository.findById("a").orElseThrow().getDanceability()).isNull();
        verify(recco).findFeatures("c");
    }

    @Test
    void changingPeriodReplacesEveryPreviousTrack() {
        when(spotify.getTopTracks(any(TimeRange.class))).thenReturn(List.of(track("b"), track("a")));
        when(recco.findFeatures(anyString())).thenReturn(Optional.of(AUDIO));
        service.importTopTracks(TimeRange.SHORT_TERM);
        when(spotify.getTopTracks(any(TimeRange.class))).thenReturn(List.of(
                new SpotifyTrack("a", "Updated", "Other Artist", "New Album", 999), track("c")));
        when(recco.findFeatures("a")).thenReturn(Optional.empty());
        service.importTopTracks(TimeRange.LONG_TERM);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findAllOrderedByRank()).extracting(Track::getSpotifyId).containsExactly("a", "c");
        Track a = repository.findById("a").orElseThrow();
        assertThat(a.getName()).isEqualTo("Updated");
        assertThat(a.getDurationMs()).isEqualTo(999);
        assertThat(a.getRank()).isEqualTo(1);
        assertThat(a.getDanceability()).isNull();
        assertThat(repository.findById("b")).isEmpty();
    }

    @Test
    void failedSpotifyRequestLeavesPreviousRankingIntact() throws Exception {
        when(spotify.getTopTracks(any(TimeRange.class))).thenReturn(List.of(track("a")));
        when(recco.findFeatures("a")).thenReturn(Optional.of(AUDIO));
        service.importTopTracks(TimeRange.MEDIUM_TERM);
        when(spotify.getTopTracks(any(TimeRange.class))).thenThrow(new ExternalApiException(HttpStatus.UNAUTHORIZED, "Token expirado"));
        mvc.perform(post("/api/import/top-tracks"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Token expirado"));
        assertThat(repository.findById("a").orElseThrow().getRank()).isEqualTo(1);
    }

    @Test
    void spotifyRateLimitIsReturnedWithRetryAfter() throws Exception {
        when(spotify.getTopTracks(any(TimeRange.class))).thenThrow(new ExternalApiException(HttpStatus.TOO_MANY_REQUESTS, "Espera", "20"));
        mvc.perform(post("/api/import/top-tracks"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "20"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void reccoRateLimitStillStoresAllSpotifyTracks() {
        when(spotify.getTopTracks(any(TimeRange.class))).thenReturn(List.of(track("a"), track("b"), track("c")));
        when(recco.findFeatures("a")).thenReturn(Optional.of(AUDIO));
        when(recco.findFeatures("b")).thenThrow(new ExternalApiException(HttpStatus.TOO_MANY_REQUESTS, "Espera"));
        var summary = service.importTopTracks(TimeRange.MEDIUM_TERM);
        assertThat(summary).isEqualTo(new TrackImportService.ImportSummary(3, 1, 2));
        assertThat(repository.count()).isEqualTo(3);
        verify(recco, never()).findFeatures("c");
    }

    @Test
    void allFiftyPositionsArePreserved() {
        var tracks = java.util.stream.IntStream.rangeClosed(1, 50).mapToObj(i -> track("id-" + i)).toList();
        when(spotify.getTopTracks(any(TimeRange.class))).thenReturn(tracks);
        when(recco.findFeatures(anyString())).thenReturn(Optional.empty());
        assertThat(service.importTopTracks(TimeRange.MEDIUM_TERM).processed()).isEqualTo(50);
        var saved = repository.findAllOrderedByRank();
        assertThat(saved).hasSize(50);
        for (int i = 0; i < 50; i++) {
            assertThat(saved.get(i).getSpotifyId()).isEqualTo(tracks.get(i).spotifyId());
            assertThat(saved.get(i).getRank()).isEqualTo(i + 1);
        }
    }

    @Test
    void databaseFailureRollsBackRankingChanges() {
        when(spotify.getTopTracks(any(TimeRange.class))).thenReturn(List.of(track("a")));
        when(recco.findFeatures(anyString())).thenReturn(Optional.empty());
        service.importTopTracks(TimeRange.MEDIUM_TERM);
        when(spotify.getTopTracks(any(TimeRange.class))).thenReturn(List.of(new SpotifyTrack("b", null, "Artist", "Album", 100)));
        assertThatThrownBy(() -> service.importTopTracks(TimeRange.MEDIUM_TERM)).isInstanceOf(RuntimeException.class);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById("a").orElseThrow().getRank()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(TimeRange.class)
    void endpointPassesTheSelectedPeriodToSpotify(TimeRange range) throws Exception {
        when(spotify.getTopTracks(range)).thenReturn(List.of(track("chosen")));
        when(recco.findFeatures("chosen")).thenReturn(Optional.empty());
        mvc.perform(post("/api/import/top-tracks").param("time_range", range.apiValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(1));
        verify(spotify).getTopTracks(range);
    }

    @Test
    void invalidPeriodDoesNotConsultSpotifyOrDeleteTracks() throws Exception {
        when(spotify.getTopTracks(TimeRange.MEDIUM_TERM)).thenReturn(List.of(track("a")));
        when(recco.findFeatures(anyString())).thenReturn(Optional.empty());
        service.importTopTracks(TimeRange.MEDIUM_TERM);
        clearInvocations(spotify);
        mvc.perform(post("/api/import/top-tracks").param("time_range", "medioum"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(spotify);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void validEmptyRankingClearsPreviousTracks() {
        when(spotify.getTopTracks(TimeRange.SHORT_TERM)).thenReturn(List.of(track("a")));
        when(recco.findFeatures(anyString())).thenReturn(Optional.empty());
        service.importTopTracks(TimeRange.SHORT_TERM);
        when(spotify.getTopTracks(TimeRange.LONG_TERM)).thenReturn(List.of());
        assertThat(service.importTopTracks(TimeRange.LONG_TERM).processed()).isZero();
        assertThat(repository.count()).isZero();
    }
}
