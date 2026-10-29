import database.MusicDatabase;
import models.Artist;
import models.User;
import processor.SpotifyProcessor;
import spotify.*;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Tops de artistas: API, JSON, SQLite y menú, sin red ni cuentas reales. */
public class ArtistTopsTest {
    static String artists(String... ids) {
        List<String> items = new ArrayList<>();
        for (String id : ids) items.add("{\"id\":\"" + id + "\",\"name\":\"Artist " + id
                + "\",\"href\":\"https://api.spotify.com/v1/artists/" + id + "\"}");
        return "{\"items\":[" + String.join(",", items) + "]}";
    }
    static List<String> rows(Path path, String table) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + path);
             var s = c.createStatement();
             var r = s.executeQuery("SELECT user_id,position,artist_id,nombre,href FROM " + table + " ORDER BY user_id,position")) {
            while (r.next()) rows.add(r.getString(1) + ":" + r.getInt(2) + ":" + r.getString(3)
                    + ":" + r.getString(4) + ":" + r.getString(5));
        }
        return rows;
    }
    static class Client extends SpotifyClient {
        final List<String> endpoints;
        Client(List<String> endpoints) { super("test-token"); this.endpoints = endpoints; }
        protected String get(String endpoint) {
            endpoints.add(endpoint);
            if (endpoint.contains("/artists?")) {
                String range = endpoint.contains("short_term") ? "short" : endpoint.contains("medium_term") ? "medium" : "long";
                return artists(range);
            }
            return "{\"items\":[]}";
        }
    }
    public static void main(String[] args) throws Exception {
        List<String> endpoints = new ArrayList<>(); Client client = new Client(endpoints);
        client.getArtistTopShort(); client.getArtistTopMedium(); client.getArtistTopLong();
        assert endpoints.equals(List.of("/me/top/artists?time_range=short_term&limit=50",
                "/me/top/artists?time_range=medium_term&limit=50", "/me/top/artists?time_range=long_term&limit=50"));
        Path path = Files.createTempFile("artist-tops-", ".sqlite");
        try {
            MusicDatabase db = new MusicDatabase(path); db.createAllTables(); db.createAllTables();
            SpotifyProcessor processor = new SpotifyProcessor(db);
            processor.addArtistTopShort(artists("b", "a"), "user");
            processor.addArtistTopShort(artists("other"), "second");
            processor.addArtistTopMedium(artists("medium"), "user");
            processor.addArtistTopLong(artists("long"), "user");
            assert rows(path, "artist_top_short").get(1).startsWith("user:1:b:Artist b:");
            assert rows(path, "artist_top_short").get(2).startsWith("user:2:a:Artist a:");
            processor.addArtistTopShort(artists("replacement"), "user");
            assert rows(path, "artist_top_short").size() == 2;
            assert rows(path, "artist_top_short").get(0).startsWith("second:1:other:");
            assert rows(path, "artist_top_medium").get(0).contains(":medium:");
            assert rows(path, "artist_top_long").get(0).contains(":long:");
            List<String> before = rows(path, "artist_top_short");
            for (String bad : List.of("{}", "not-json", "{\"items\":[{\"id\":\"x\"}]}", artists("duplicate", "duplicate"))) {
                try { processor.addArtistTopShort(bad, "user"); throw new AssertionError("Invalid JSON accepted"); }
                catch (IOException expected) { assert before.equals(rows(path, "artist_top_short")); }
            }
            // Error después del DELETE: rollback conserva todo el top anterior.
            try {
                db.replaceArtistTopShort("user", List.of(new Artist("duplicate", "One", null), new Artist("duplicate", "Two", null)));
                throw new AssertionError("Duplicate artist accepted");
            } catch (SQLException expected) { assert before.equals(rows(path, "artist_top_short")); }
            processor.addArtistTopShort(artists(), "user");
            assert rows(path, "artist_top_short").size() == 1;
            db.createUser(new User("user", "test-token", "unused", Instant.now().plusSeconds(3600).toString(), "now"));
            endpoints.clear();
            BackendConsole console = new BackendConsole(db, new Scanner("1\n6\n7\n8\n0\n0\n")) {
                protected SpotifyLogin createLogin(MusicDatabase database) { return new SpotifyLogin(null, database); }
                protected SpotifyClient createClient(String token) { assert token.equals("test-token"); return new Client(endpoints); }
            };
            console.start();
            assert endpoints.size() == 3;
            assert rows(path, "artist_top_short").stream().anyMatch(row -> row.startsWith("user:1:short:"));
            assert rows(path, "artist_top_medium").get(0).startsWith("user:1:medium:");
            assert rows(path, "artist_top_long").get(0).startsWith("user:1:long:");
            endpoints.clear();
            new BackendConsole(db, new Scanner("1\n5\n0\n0\n")) {
                protected SpotifyLogin createLogin(MusicDatabase database) { return new SpotifyLogin(null, database); }
                protected SpotifyClient createClient(String token) { return new Client(endpoints); }
            }.start();
            assert endpoints.size() == 7;
            assert endpoints.stream().filter(endpoint -> endpoint.contains("/artists?")).count() == 3;
            db.deleteAllTables(); db.createAllTables();
            assert rows(path, "artist_top_short").isEmpty() && rows(path, "artist_top_medium").isEmpty() && rows(path, "artist_top_long").isEmpty();
            System.out.println("PASS: artist endpoints, JSON mapping, ranking order, isolated users/ranges, replacement/empty lists, rollback, invalid responses, menu individual/all, schema lifecycle.");
        } finally { Files.delete(path); }
    }
}
