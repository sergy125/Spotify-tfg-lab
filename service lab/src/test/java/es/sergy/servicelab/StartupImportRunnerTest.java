package es.sergy.servicelab;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpStatus;

class StartupImportRunnerTest {
    private final TrackImportService service = mock(TrackImportService.class);
    private final ByteArrayOutputStream console = new ByteArrayOutputStream();

    private StartupImportRunner runner(String choice, String configured) {
        return new StartupImportRunner(service, configured,
                new ByteArrayInputStream(choice.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(console, true, StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @CsvSource({"1,SHORT_TERM", "2,MEDIUM_TERM", "3,LONG_TERM", "short_term,SHORT_TERM",
            "medium_term,MEDIUM_TERM", "long_term,LONG_TERM"})
    void menuImportsTheSelectedPeriod(String choice, TimeRange expected) {
        when(service.importTopTracks(expected)).thenReturn(new TrackImportService.ImportSummary(50, 40, 10));
        runner(choice + "\n", "").run(new DefaultApplicationArguments());
        verify(service).importTopTracks(expected);
        assertThat(console.toString(StandardCharsets.UTF_8)).contains("short_term", "medium_term", "long_term");
    }

    @Test
    void invalidChoiceAsksAgainWithoutImportingTheWrongPeriod() {
        when(service.importTopTracks(TimeRange.SHORT_TERM)).thenReturn(new TrackImportService.ImportSummary(50, 40, 10));
        runner("medioum\n1\n", "").run(new DefaultApplicationArguments());
        verify(service).importTopTracks(TimeRange.SHORT_TERM);
        verifyNoMoreInteractions(service);
        assertThat(console.toString(StandardCharsets.UTF_8)).contains("Opción no válida");
    }

    @Test
    void cancelDoesNotImportOrDeleteAnything() {
        runner("0\n", "").run(new DefaultApplicationArguments());
        verifyNoInteractions(service);
        assertThat(console.toString(StandardCharsets.UTF_8)).contains("no se ha modificado");
    }

    @Test
    void missingInputDoesNotChooseADefaultOrDeleteAnything() {
        runner("", "").run(new DefaultApplicationArguments());
        verifyNoInteractions(service);
    }

    @Test
    void configuredPeriodWorksWithoutAnInteractiveConsole() {
        when(service.importTopTracks(TimeRange.LONG_TERM)).thenReturn(new TrackImportService.ImportSummary(50, 40, 10));
        runner("", "long_term").run(new DefaultApplicationArguments());
        verify(service).importTopTracks(TimeRange.LONG_TERM);
        assertThat(console.size()).isZero();
    }

    @Test
    void expiredTokenDoesNotStopTheHttpServer() {
        when(service.importTopTracks(TimeRange.SHORT_TERM))
                .thenThrow(new ExternalApiException(HttpStatus.UNAUTHORIZED, "Token expirado"));
        assertThatCode(() -> runner("1\n", "").run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }
}
