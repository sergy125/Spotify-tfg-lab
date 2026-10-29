package audiofeatures;

import com.google.gson.*;
import database.MusicDatabase;
import models.Song;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** Comprueba la correspondencia JSON -> ID -> Song -> fila SQLite. Sin red. */
public class IAfeaturesMappingTest {
    static class FakeIA extends IAfeatures {
        int calls;
        String invalid;
        protected String request(JsonObject body) {
            calls++;
            String prompt = body.getAsJsonArray("contents").get(0).getAsJsonObject()
                    .getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
            JsonArray input = JsonParser.parseString(prompt.substring(prompt.indexOf("Songs: ") + 7)).getAsJsonArray();
            JsonArray output = new JsonArray();
            // Devuelve el grupo en orden inverso y valores distintos para cada ID.
            for (int i = input.size() - 1; i >= 0; i--) {
                String id = input.get(i).getAsJsonObject().get("id").getAsString();
                int n = Integer.parseInt(id.substring("spotify-".length()));
                JsonObject row = new JsonObject(); row.addProperty("id", id);
                for (String field : List.of("acousticness", "danceability", "energy", "instrumentalness",
                        "liveness", "speechiness", "valence")) row.addProperty(field, n / 100.0);
                row.addProperty("key", n % 12); row.addProperty("mode", n % 2);
                row.addProperty("tempo", 80 + n); row.addProperty("loudness", -5 - n);
                row.addProperty("popularity", n);
                output.add(row);
            }
            JsonObject last = output.get(output.size() - 1).getAsJsonObject();
            if (invalid != null) switch (invalid) {
                case "missing" -> output.remove(output.size() - 1);
                case "duplicate" -> output.add(last.deepCopy());
                case "unknown" -> last.addProperty("id", "other-song");
                case "no-id" -> last.remove("id");
                case "null-id" -> last.add("id", JsonNull.INSTANCE);
                case "invalid-last" -> last.add("tempo", JsonNull.INSTANCE);
                case "previous-batch" -> { if (calls == 2) last.addProperty("id", "spotify-1"); }
            }
            return output.toString();
        }
    }
    static Song song(int n) {
        Song song = new Song(); song.setId("spotify-" + n);
        // Mismo título y artista para todas: la identidad tiene que depender del ID.
        song.setNombre("Mismo título"); song.setArtistas("Mismo artista");
        song.setAlbum("Album " + n); return song;
    }
    public static void main(String[] args) throws Exception {
        List<Song> songs = new ArrayList<>();
        for (int n = 1; n <= 41; n++) songs.add(song(n));
        songs.get(0).setEnergy(.99);
        FakeIA ai = new FakeIA(); ai.addFeatures(songs);
        assert ai.calls == 3;
        var path = Files.createTempFile("ia-mapping-", ".sqlite");
        try {
            MusicDatabase db = new MusicDatabase(path); db.createAllTables();
            for (int i = 0; i < songs.size(); i++) {
                Song song = songs.get(i); int n = i + 1;
                assert song.getId().equals("spotify-" + n);
                assert song.getAcousticness() == n / 100.0;
                assert song.getDanceability() == n / 100.0;
                assert song.getEnergy() == (n == 1 ? .99 : n / 100.0);
                assert song.getInstrumentalness() == n / 100.0;
                assert song.getLiveness() == n / 100.0;
                assert song.getSpeechiness() == n / 100.0;
                assert song.getValence() == n / 100.0;
                assert song.getTempo() == 80 + n && song.getLoudness() == -5 - n;
                assert song.getKey() == n % 12 && song.getMode() == n % 2;
                assert song.getPopularidad() == n && song.isAi();
                assert song.getNombre().equals("Mismo título") && song.getAlbum().equals("Album " + n);
                db.createSong(song);
                Song loaded = db.getSong("spotify-" + n).orElseThrow();
                assert new Gson().toJson(song).equals(new Gson().toJson(loaded)) : "Cruce de datos en SQLite";
            }
        } finally { Files.delete(path); }
        for (String invalid : List.of("missing", "duplicate", "unknown", "no-id", "null-id", "invalid-last")) {
            FakeIA bad = new FakeIA(); bad.invalid = invalid;
            List<Song> batch = List.of(song(1), song(2), song(3));
            String before = new Gson().toJson(batch);
            try { bad.addFeatures(batch); throw new AssertionError(invalid + " aceptado"); }
            catch (IOException expected) { assert before.equals(new Gson().toJson(batch)) : "Grupo parcialmente modificado"; }
        }
        FakeIA crossBatch = new FakeIA(); crossBatch.invalid = "previous-batch";
        List<Song> batch = new ArrayList<>(); for (int i = 1; i <= 21; i++) batch.add(song(i));
        try { crossBatch.addFeatures(batch); throw new AssertionError("ID de otro grupo aceptado"); }
        catch (IOException expected) {
            assert batch.get(0).getPopularidad() == 1 && batch.get(0).isAi();
            assert batch.get(20).hasMissingFeatures() && !batch.get(20).isAi();
        }
        System.out.println("PASS: 41 canciones, 3 grupos invertidos, mismo título, todas las features/popularidad por ID, persistencia SQLite y rechazo de IDs erróneos sin cambios parciales.");
    }
}
