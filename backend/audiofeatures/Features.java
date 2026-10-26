package audiofeatures;

import models.Song;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

public class Features {
    private final ReccoBeats reccoBeats;
    private final IAfeatures ia;

    public Features() { this(new ReccoBeats(), new IAfeatures()); }

    Features(ReccoBeats reccoBeats, IAfeatures ia) {
        this.reccoBeats = reccoBeats;
        this.ia = ia;
    }

    public Song addFeatures(Song song) throws IOException, InterruptedException {
        return reccoBeats.addFeatures(song);
    }

    public void addFeatures(List<Song> songs) throws IOException, InterruptedException {
        List<Song> notFound = new ArrayList<>();
        for (Song song : songs) {
            if ((song.hasMissingFeatures() || song.getPopularidad() == null)
                    && !reccoBeats.findFeatures(song)) notFound.add(song);
        }
        ia.addFeatures(notFound);
    }
}
