package audiofeatures;

import models.Song;

import java.io.IOException;

public class Features {
    private final ReccoBeats reccoBeats = new ReccoBeats();

    public Song addFeatures(Song song) throws IOException, InterruptedException {
        song = reccoBeats.addFeatures(song);

        if (song.getAcousticness() == null && song.getDanceability() == null
                && song.getEnergy() == null && song.getInstrumentalness() == null
                && song.getKey() == null && song.getLiveness() == null
                && song.getLoudness() == null && song.getMode() == null
                && song.getSpeechiness() == null && song.getTempo() == null
                && song.getValence() == null) {
            // TODO: Call the AI features class here when ReccoBeats has no audio features.
        }

        return song;
    }
}
