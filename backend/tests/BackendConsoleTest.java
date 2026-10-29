import audiofeatures.ReccoBeats;
import com.google.gson.Gson;
import database.MusicDatabase;
import models.Song;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Revisión del menú con SQLite temporal y ReccoBeats simulado, sin red. */
public class BackendConsoleTest {
    private static Song song(String id, boolean ai) {
        Song song = new Gson().fromJson("{\"acousticness\":0.1,\"danceability\":0.5,\"energy\":0.7,"
                + "\"instrumentalness\":0,\"key\":4,\"liveness\":0.1,\"loudness\":-6,\"mode\":1,"
                + "\"speechiness\":0.1,\"tempo\":120,\"valence\":0.6,\"popularidad\":50}", Song.class);
        song.setId(id); song.setNombre(id); song.setArtistas("Artist"); song.setAi(ai);
        return song;
    }
    public static void main(String[] args) throws Exception {
        var file = Files.createTempFile("review-ai-", ".sqlite");
        MusicDatabase db = new MusicDatabase(file); db.createAllTables();
        assert db.listUsers().isEmpty() && db.listAiSongIds().isEmpty();
        for (String id : List.of("found", "absent", "partial", "error")) db.createSong(song(id, true));
        db.createSong(song("original-recco", false));
        assert db.listAiSongIds().equals(List.of("absent", "error", "found", "partial"));
        Map<String, String> before = new HashMap<>();
        Gson gson = new Gson();
        for (String id : List.of("absent", "partial", "error", "original-recco"))
            before.put(id, gson.toJson(db.getSong(id).orElseThrow()));
        List<String> queried = new ArrayList<>();
        BackendConsole console = new BackendConsole(db, new Scanner("2\n0\n")) {
            protected ReccoBeats createReccoBeats() {
                return new ReccoBeats() {
                    public boolean findFeatures(Song song) throws IOException {
                        queried.add(song.getId());
                        song.setPopularidad(90);
                        switch (song.getId()) {
                            case "found" -> { song.setEnergy(.99); song.setAi(false); return true; }
                            case "partial" -> { song.setTempo(null); return false; }
                            case "error" -> throw new IOException("offline");
                            default -> { return false; }
                        }
                    }
                };
            }
            protected spotify.SpotifyLogin createLogin(MusicDatabase database) {
                throw new AssertionError("Esta opción no requiere Spotify");
            }
        };
        PrintStream output = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured));
            console.start();
        } finally { System.setOut(output); }
        assert queried.size() == 4 && !queried.contains("original-recco");
        Song updated = db.getSong("found").orElseThrow();
        assert !updated.isAi() && updated.getEnergy() == .99 && updated.getPopularidad() == 90;
        for (String id : before.keySet())
            assert before.get(id).equals(gson.toJson(db.getSong(id).orElseThrow())) : id + " cambió";
        assert captured.toString().contains("Se han encontrado las features de esta canción: found");
        assert captured.toString().contains("[Spotify ID: error]: offline");
        assert captured.toString().contains("Actualizadas: 1. Conservadas con IA: 3. Errores: 1.");
        assert db.listAiSongIds().equals(List.of("absent", "error", "partial"));
        db.createUser(new models.User("z-user"));
        db.createUser(new models.User("a-user"));
        assert db.listUsers().equals(List.of("a-user", "z-user"));
        BackendConsole failedLogin = new BackendConsole(db, new Scanner("3\n0\n")) {
            protected spotify.SpotifyLogin createLogin(MusicDatabase database) {
                return new spotify.SpotifyLogin(null, database) {
                    public models.User login() throws IOException {
                        throw new IOException("Tiempo de inicio de sesión agotado.");
                    }
                };
            }
        };
        captured.reset();
        try {
            System.setOut(new PrintStream(captured));
            failedLogin.start();
        } finally { System.setOut(output); }
        assert captured.toString().contains("Motivo del fallo de inicio de sesión: Tiempo de inicio de sesión agotado.");
        Files.delete(file);
        System.out.println("PASS: menu sin login, solo ai=1, sustitución completa, conservación ante ausencia/respuesta parcial/error, aviso y resumen.");
    }
}
