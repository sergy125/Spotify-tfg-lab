package statistics;

import models.Song;

import java.util.HashMap;
import java.util.Optional;
import java.util.Comparator;
import java.util.List;

public record ListeningHistoryStatistics(HashMap<String, Song> songsByPlayedAt) {
    public Optional<String> closestTaylorSwiftEra() {
        double energy = 0;
        double valence = 0;
        double acousticness = 0;
        int plays = 0;
        for (Song song : songsByPlayedAt.values()) {
            if (!isNormalized(song.getEnergy()) || !isNormalized(song.getValence())
                    || !isNormalized(song.getAcousticness())) continue;
            energy += song.getEnergy();
            valence += song.getValence();
            acousticness += song.getAcousticness();
            plays++;
        }
        if (plays == 0) return Optional.empty();

        double averageEnergy = energy / plays;
        double averageValence = valence / plays;
        double averageAcousticness = acousticness / plays;
        return TAYLOR_ERAS.stream()
                .min(Comparator.comparingDouble(era -> era.distanceSquared(averageEnergy, averageValence, averageAcousticness)))
                .map(EraProfile::name);
    }

    private static boolean isNormalized(Double value) {
        return value != null && value >= 0 && value <= 1;
    }

    // Hand-picked estimates in the order energy, valence, acousticness.
    private static final List<EraProfile> TAYLOR_ERAS = List.of(
            new EraProfile("Taylor Swift (Debut)", .61, .60, .46),
            new EraProfile("Fearless", .69, .63, .30),
            new EraProfile("Speak Now", .75, .50, .25),
            new EraProfile("Red", .61, .44, .35),
            new EraProfile("1989", .75, .65, .09),
            new EraProfile("Reputation", .78, .38, .11),
            new EraProfile("Lover", .60, .67, .25),
            new EraProfile("Folklore", .33, .31, .79),
            new EraProfile("Evermore", .43, .36, .68),
            new EraProfile("Midnights", .53, .45, .22),
            new EraProfile("The Tortured Poets Department", .45, .29, .49),
            new EraProfile("The Life of a Showgirl", .68, .74, .19));

    private record EraProfile(String name, double energy, double valence, double acousticness) {
        double distanceSquared(double averageEnergy, double averageValence, double averageAcousticness) {
            double energyDifference = energy - averageEnergy;
            double valenceDifference = valence - averageValence;
            double acousticnessDifference = acousticness - averageAcousticness;
            return energyDifference * energyDifference + valenceDifference * valenceDifference
                    + acousticnessDifference * acousticnessDifference;
        }
    }
}
