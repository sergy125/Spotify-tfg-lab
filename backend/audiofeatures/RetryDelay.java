package audiofeatures;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Interpreta Retry-After para ambas APIs; si falta, usa la espera progresiva. */
final class RetryDelay {
    private RetryDelay() { }

    static Duration fromHeader(String retryAfter, long fallbackSeconds) {
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            if (seconds >= 0) return Duration.ofSeconds(Math.max(seconds, 1));
        } catch (NumberFormatException ex) {
            try {
                Instant retryAt = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration delay = Duration.between(Instant.now(), retryAt);
                return delay.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : delay;
            } catch (DateTimeParseException ignored) {
                // Cabecera ausente o inválida: espera progresiva de 5 a 60 segundos.
            }
        }
        return Duration.ofSeconds(fallbackSeconds);
    }

}
