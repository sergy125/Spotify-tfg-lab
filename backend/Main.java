import database.MusicDatabase;
import models.Song;

import java.sql.SQLException;

public class Main {
    public static void main(String[] args) throws SQLException {
        MusicDatabase database = new MusicDatabase();
        database.createAllTables();
        System.out.println("Tablas creadas en database/lab.");
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
