package es.sergy.servicelab;

import java.util.Locale;

public enum TimeRange {
    SHORT_TERM("short_term", "Corto: aproximadamente las últimas 4 semanas"),
    MEDIUM_TERM("medium_term", "Medio: aproximadamente los últimos 6 meses"),
    LONG_TERM("long_term", "Largo: aproximadamente el último año, según Spotify");

    private final String apiValue;
    private final String description;

    TimeRange(String apiValue, String description) {
        this.apiValue = apiValue;
        this.description = description;
    }

    public String apiValue() {
        return apiValue;
    }

    public String description() {
        return description;
    }

    public static TimeRange fromApiValue(String value) {
        for (TimeRange range : values()) {
            if (range.apiValue.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return range;
            }
        }
        throw new IllegalArgumentException("Usa short_term, medium_term o long_term.");
    }

    public static TimeRange fromMenuChoice(String value) {
        return switch (value.trim()) {
            case "1" -> SHORT_TERM;
            case "2" -> MEDIUM_TERM;
            case "3" -> LONG_TERM;
            default -> fromApiValue(value);
        };
    }
}
