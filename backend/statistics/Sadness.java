package statistics;

import models.Song;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Stream;

final class Sadness {
    private Sadness() {}

    static OptionalDouble averageScore(Stream<Song> songs) {
        return songs.map(Song::getValence)
                .filter(Objects::nonNull)
                .filter(valence -> valence >= 0 && valence <= 1)
                .mapToDouble(valence -> 10 - 9 * valence)
                .average();
    }
}
