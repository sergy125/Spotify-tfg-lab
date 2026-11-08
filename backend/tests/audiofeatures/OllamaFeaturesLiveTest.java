package audiofeatures;

import database.MusicDatabase;
import models.Song;
import java.nio.file.Files;
import java.util.List;

/** Prueba local optativa: requiere Ollama; nunca accede a Spotify/Gemini ni a la base real. */
public class OllamaFeaturesLiveTest {
    public static void main(String[] args) throws Exception {
        Song song = new Song(); song.setId("local-test-song");
        song.setNombre("Billie Jean"); song.setArtistas("Michael Jackson"); song.setAlbum("Thriller");
        song.setEnergy(.77);
        var features = new Features(new ReccoBeats() {
            @Override public boolean findFeatures(Song input) { return false; }
        }, new OllamaFeatures());
        features.addFeatures(song);
        assert !song.hasMissingFeatures() && song.getPopularidad() != null && song.isAi();
        assert song.getEnergy() == .77;
        var path = Files.createTempFile("ollama-features-live-", ".sqlite");
        try {
            var db = new MusicDatabase(path); db.createAllTables(); db.createSong(song);
            Song saved = db.getSong(song.getId()).orElseThrow();
            assert saved.isAi() && !saved.hasMissingFeatures() && saved.getEnergy() == .77;
        } finally { Files.deleteIfExists(path); }
        // Ya completa: no debe consultar ReccoBeats ni Ollama.
        features.addFeatures(song);
        System.out.println("PASS: ReccoBeats sin resultado -> Ollama real -> validación -> SQLite temporal; datos previos conservados.");
    }
}
