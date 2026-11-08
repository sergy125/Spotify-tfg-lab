package statistics;

import database.MusicDatabase.TopPeriod;
import models.Song;

import java.util.HashMap;
import java.util.OptionalDouble;

public record TopTracksStatistics(HashMap<Integer, Song> shortTerm,
                                  HashMap<Integer, Song> mediumTerm,
                                  HashMap<Integer, Song> longTerm) {
    public OptionalDouble averageSadness(TopPeriod period) {
        var tracks = switch (period) {
            case SHORT_TERM -> shortTerm;
            case MEDIUM_TERM -> mediumTerm;
            case LONG_TERM -> longTerm;
        };
        return Sadness.averageScore(tracks.values().stream());
    }
}
