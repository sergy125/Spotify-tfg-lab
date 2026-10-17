package es.sergy.servicelab.imports;

import es.sergy.servicelab.errors.ExternalApiException;
import es.sergy.servicelab.spotify.TimeRange;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

@Component
@ConditionalOnProperty(name = "import.on-startup", havingValue = "true", matchIfMissing = true)
public class StartupImportRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupImportRunner.class);
    private final TrackImportService service;
    private final String configuredRange;
    private final BufferedReader input;
    private final PrintStream output;

    @Autowired
    public StartupImportRunner(TrackImportService service, @Value("${import.time-range:}") String configuredRange) {
        this(service, configuredRange, System.in, System.out);
    }

    StartupImportRunner(TrackImportService service, String configuredRange, InputStream input, PrintStream output) {
        this.service = service;
        this.configuredRange = configuredRange;
        this.input = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.output = output;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            TimeRange timeRange = chooseTimeRange();
            if (timeRange == null) {
                return;
            }
            log.info("Importando hasta 50 canciones de {}. El ranking anterior se sustituirá al guardar.",
                    timeRange.apiValue());
            var summary = service.importTopTracks(timeRange);
            log.info("Importación terminada: {} canciones guardadas, {} con audio y {} sin audio.",
                    summary.processed(), summary.withReccoBeats(), summary.withoutReccoBeats());
            log.info("Base de datos actualizada. Puedes consultar GET /api/tracks o detener el programa con Stop.");
        } catch (ExternalApiException ex) {
            log.error("Importación pendiente (HTTP {}): {}", ex.getStatus().value(), ex.getMessage());
        } catch (DataAccessException | TransactionException ex) {
            log.error("No se pudo guardar el ranking. Comprueba la ruta y los permisos del archivo SQLite.");
        } catch (IllegalArgumentException | IOException ex) {
            log.error("No se ha importado ningún dato: {}", ex.getMessage());
        }
    }

    private TimeRange chooseTimeRange() throws IOException {
        if (!configuredRange.isBlank()) {
            return TimeRange.fromApiValue(configuredRange);
        }
        output.println("\nSERVICE LAB — Elige el periodo de Spotify");
        TimeRange[] ranges = TimeRange.values();
        for (int i = 0; i < ranges.length; i++) {
            output.printf("  %d) %s — %s%n", i + 1, ranges[i].apiValue(), ranges[i].description());
        }
        output.println("Se reemplazarán todas las canciones de tracks por el ranking elegido.");
        while (true) {
            output.print("Escribe 1, 2 o 3 y pulsa Enter (0 para cancelar): ");
            output.flush();
            String choice = input.readLine();
            if (choice == null || choice.trim().equals("0")) {
                output.println("Importación cancelada. La base de datos no se ha modificado.");
                return null;
            }
            try {
                return TimeRange.fromMenuChoice(choice);
            } catch (IllegalArgumentException ex) {
                output.println("Opción no válida. Elige 1, 2, 3 o el nombre exacto del periodo.");
            }
        }
    }
}
