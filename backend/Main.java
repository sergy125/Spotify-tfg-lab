import database.MusicDatabase;
import models.Song;
import processor.SpotifyProcessor;
import spotify.SpotifyClient;

import java.io.IOException;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) throws SQLException, IOException, InterruptedException {
        String token = "BQBhvu5pBblZhn8i9lMYobcYH3yKHRooZCIkzf7CG3jedi8MgTD6oosvZvpuZwVtGXNLMrxDd7MsSo56iMll9nvuOVZgpHCh3j7VLCw1X4E6S8Lj8CnaWKX9ldv3Vsi4rRyGFT9faHjZrPRJC-_7acEZkrfoD5C4IzMbiewFk_v48tA21PFxVqlaslDneBEv4bdVki_KLfRUrd6jSLAnQZM8qYgPYTMZ4rX5Qb6qyEF_3n7tNN02fkrigE7V9P1U4bU3PsICy1HBs9rNar6PCcOkIN2JL2_0NMITlOAH0ZN0IWEsZEFSWSbAAa6ASlhtlg2WaxWe";
        MusicDatabase database = new MusicDatabase();
        database.createAllTables();

        SpotifyClient client = new SpotifyClient(token);
        String json = client.getTopShort();

        SpotifyProcessor processor = new SpotifyProcessor(database);
        processor.addTopShort(json, "9999");

        System.out.println("Short-term top saved to Songs and top_short for user 9999.");
        System.out.println(formatJson(json));
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
