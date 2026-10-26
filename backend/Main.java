import database.MusicDatabase;
import models.Song;
import models.User;
import processor.SpotifyProcessor;
import spotify.SpotifyClient;
import spotify.SpotifyAuth;
import spotify.SpotifyLogin;

import java.io.IOException;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) throws SQLException, IOException, InterruptedException {
        new BackendConsole().start();

        /* Código anterior:
        SpotifyAuth auth = new SpotifyAuth();
        MusicDatabase database = new MusicDatabase();
        database.createAllTables();

        SpotifyLogin login = new SpotifyLogin(auth, database);
        User user = login.login();
        System.out.println("Usuario conectado: " + user.getUserId());

        SpotifyClient client = new SpotifyClient(user.getAccessToken());
        SpotifyProcessor processor = new SpotifyProcessor(database);
        getHistory(client, processor, user.getUserId());
        getTopShort(client, processor, user.getUserId());
        getTopMedium(client, processor, user.getUserId());
        getTopLong(client, processor, user.getUserId());
        */
    }

    private static void getHistory(SpotifyClient client, SpotifyProcessor processor, String userId)
            throws IOException, InterruptedException, SQLException {
        String json = client.getHistory();
        processor.addHistory(json, userId);
        System.out.println("history saved");
    }

    private static void getTopShort(SpotifyClient client, SpotifyProcessor processor, String userId)
            throws IOException, InterruptedException, SQLException {
        String json = client.getTopShort();
        processor.addTopShort(json, userId);
        System.out.println("top short saved");
    }

    private static void getTopMedium(SpotifyClient client, SpotifyProcessor processor, String userId)
            throws IOException, InterruptedException, SQLException {
        String json = client.getTopMedium();
        processor.addTopMedium(json, userId);
        System.out.println("top medium saved");
    }

    private static void getTopLong(SpotifyClient client, SpotifyProcessor processor, String userId)
            throws IOException, InterruptedException, SQLException {
        String json = client.getTopLong();
        processor.addTopLong(json, userId);
        System.out.println("top long saved");
    }

    private static String formatJson(String json) {
        StringBuilder formatted = new StringBuilder();
        int indentation = 0;
        boolean insideString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char character = json.charAt(i);

            if (insideString) {
                formatted.append(character);
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    insideString = false;
                }
                continue;
            }

            switch (character) {
                case '"' -> {
                    insideString = true;
                    formatted.append(character);
                }
                case '{', '[' -> {
                    formatted.append(character).append('\n');
                    indentation++;
                    formatted.append("\t".repeat(indentation));
                }
                case '}', ']' -> {
                    indentation--;
                    formatted.append('\n').append("\t".repeat(indentation));
                    formatted.append(character);
                }
                case ',' -> formatted.append(character).append('\n')
                        .append("\t".repeat(indentation));
                case ':' -> formatted.append(": ");
                default -> {
                    if (!Character.isWhitespace(character)) {
                        formatted.append(character);
                    }
                }
            }
        }
        return formatted.toString();
    }

    private void createTable() throws SQLException {
        MusicDatabase database = new MusicDatabase();
        database.createAllTables();
        // Datos ficticios para comprobar la inserción de todos los atributos.
        Song song1 = new Song(
                "demo-song-1", "Noche de verano", "Artista de ejemplo", "Primer álbum",
                210000, 75, "https://example.com/songs/demo-song-1", "ESAAA2600001",
                0.15, 0.82, 0.78, 0.0, 5, 0.12, -5.4, 1, 0.06, 120.0, 0.85);

        Song song2 = new Song(
                "demo-song-2", "Lluvia tranquila", "Banda de ejemplo", "Segundo álbum",
                185000, 60, "https://example.com/songs/demo-song-2", "ESAAA2600002",
                0.88, 0.35, 0.25, 0.72, 9, 0.08, -12.3, 0, 0.03, 85.0, 0.40);

        for (Song song : new Song[]{song1, song2}) {
            if (database.getSong(song.getId()).isEmpty()) {
                database.createSong(song);
                System.out.println("Canción insertada: " + song.getNombre());
            } else {
                System.out.println("La canción ya existe: " + song.getNombre());
            }
        }

    }
}
