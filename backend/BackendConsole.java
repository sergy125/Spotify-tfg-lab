import database.MusicDatabase;
import database.MusicDatabase.TopPeriod;
import models.User;
import models.Song;
import audiofeatures.ReccoBeats;
import processor.SpotifyProcessor;
import spotify.SpotifyAuth;
import spotify.SpotifyClient;
import spotify.SpotifyLogin;
import statistics.UserStatistics;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** Menú de cuentas y actualización de datos del backend de terminal. */
public class BackendConsole {
    private final MusicDatabase database;
    private final Scanner input;
    private SpotifyLogin login;

    public BackendConsole() {
        this(new MusicDatabase(), new Scanner(System.in));
    }

    BackendConsole(MusicDatabase database, Scanner input) {
        this.database = database;
        this.input = input;
    }

    public void start() throws SQLException, InterruptedException {
        database.createAllTables();
        while (true) {
            List<String> users = database.listUsers();
            System.out.println("\n¿Qué cuenta de Spotify quieres usar de la tabla Users?");
            for (int i = 0; i < users.size(); i++) {
                System.out.println((i + 1) + ". " + users.get(i));
            }
            if (users.isEmpty()) System.out.println("No hay usuarios registrados.");
            int newAccount = users.size() + 1;
            int reviewAi = newAccount + 1;
            System.out.println(newAccount + ". Conectar un nuevo Spotify (o volver a autorizar una cuenta)");
            System.out.println(reviewAi + ". Buscar en ReccoBeats las features de canciones estimadas con IA");
            System.out.println("0. Salir");
            int choice = readOption(reviewAi);
            if (choice == 0) return;
            if (choice == reviewAi) {
                reviewAiSongs();
                continue;
            }
            try {
                if (login == null) login = createLogin(database);
                User user = choice == newAccount ? login.login()
                        : login.getValidUser(users.get(choice - 1));
                System.out.println("Usuario conectado: " + user.getUserId());
                updateMenu(user.getUserId());
            } catch (IOException | IllegalArgumentException ex) {
                System.out.println("Motivo del fallo de inicio de sesión: " + ex.getMessage());
                System.out.println("No se pudo conectar la cuenta. Revisa la conexión y las variables "
                        + "SPOTIFY_CLIENT_ID y SPOTIFY_CLIENT_SECRET. Si la autorización ha caducado "
                        + "o se ha revocado, elige conectar un nuevo Spotify para autorizarla de nuevo.");
            }
        }
    }

    private void reviewAiSongs() throws SQLException, InterruptedException {
        List<String> ids = database.listAiSongIds();
        System.out.println("Canciones con IA para revisar: " + ids.size());
        ReccoBeats reccoBeats = createReccoBeats();
        int updated = 0;
        int errors = 0;
        for (String id : ids) {
            // Los cambios se guardan solo si la respuesta tiene todas las audiofeatures.
            Song song = database.getSong(id).orElseThrow();
            try {
                if (reccoBeats.findFeatures(song) && !song.hasMissingFeatures()) {
                    song.setAi(false);
                    if (database.updateSong(song)) {
                        updated++;
                        System.out.println("Se han encontrado las features de esta canción: "
                                + song.getNombre() + " — " + song.getArtistas() + ". ai = 0");
                    }
                }
            } catch (IOException ex) {
                errors++;
                System.out.println("No se pudo consultar " + song.getNombre() + " [Spotify ID: " + id
                        + "]: " + ex.getMessage() + "; se conservan sus datos de IA.");
            }
        }
        System.out.println("Revisión terminada. Actualizadas: " + updated
                + ". Conservadas con IA: " + (ids.size() - updated) + ". Errores: " + errors + ".");
    }

    private void updateMenu(String userId) throws InterruptedException {
        SpotifyProcessor processor = new SpotifyProcessor(database);
        UserStatistics statistics = null;
        while (true) {
            System.out.println("\n¿Qué tabla quieres actualizar para " + userId + "?");
            System.out.println("1. Historial\n2. top_short\n3. top_medium\n4. top_long"
                    + "\n5. Todas (historial, canciones y artistas)\n6. artist_top_short"
                    + "\n7. artist_top_medium\n8. artist_top_long\n9. Statistics\n0. Volver a las cuentas");
            System.out.println("Songs se completa automáticamente con las canciones nuevas.");
            int choice = readOption(9);
            if (choice == 0) return;
            if (choice == 9) {
                if (statistics == null) statistics = refreshStatistics(userId, null);
                if (statistics != null) statisticsMenu(statistics);
                continue;
            }
            UserStatistics previousStatistics = statistics;
            statistics = null;
            try {
                if (choice == 5) {
                    for (int table : new int[]{1, 2, 3, 4, 6, 7, 8}) update(table, userId, processor);
                    statistics = refreshStatistics(userId, previousStatistics);
                } else {
                    update(choice, userId, processor);
                }
            } catch (IOException | SQLException ex) {
                System.out.println("Motivo del fallo de actualización: " + ex.getMessage());
                System.out.println("No se pudo completar la actualización. Las tablas anteriores que "
                        + "se hayan confirmado como actualizadas se conservan. Puedes reintentar o "
                        + "volver a las cuentas y autorizar Spotify de nuevo.");
            }
        }
    }

    private UserStatistics refreshStatistics(String userId, UserStatistics statistics) {
        try {
            if (statistics == null) return new UserStatistics(userId, database);
            statistics.refreshAll();
            return statistics;
        } catch (SQLException ex) {
            System.out.println("Could not load statistics data: " + ex.getMessage());
            return null;
        }
    }

    private void statisticsMenu(UserStatistics statistics) {
        TopPeriod[] periods = TopPeriod.values();
        while (true) {
            System.out.println("\nStatistics");
            System.out.println("1. Average Sadness — Listening History");
            System.out.println("2. Average Sadness — Top Tracks, Short Term\n3. Average Sadness — Top Tracks, Medium Term\n4. Average Sadness — Top Tracks, Long Term");
            System.out.println("5. Which Taylor Swift Era Are You?");
            System.out.println("0. Back");
            int choice = readOption(5, "Enter a number from 0 to 5.");
            if (choice == 0) return;
            if (choice == 5) {
                System.out.println(statistics.closestTaylorSwiftEra()
                        .map(era -> "You're in your " + era + " era!")
                        .orElse("No plays with valid energy, valence, and acousticness data."));
                continue;
            }
            var score = choice == 1 ? statistics.historySadness()
                    : statistics.topTracksSadness(periods[choice - 2]);
            if (score.isPresent()) {
                System.out.printf(Locale.ROOT, "Average sadness: %.1f/10 (10 = saddest)%n", score.getAsDouble());
            } else {
                System.out.println("No matching tracks with valence data.");
            }
        }
    }

    private void update(int table, String userId, SpotifyProcessor processor)
            throws SQLException, IOException, InterruptedException {
        // Se comprueba también después de esperar en el menú y entre tablas.
        User user = login.getValidUser(userId);
        SpotifyClient client = createClient(user.getAccessToken());
        String name;
        switch (table) {
            case 1 -> { processor.addHistory(client.getHistory(), userId); name = "Historial"; }
            case 2 -> { processor.addTopShort(client.getTopShort(), userId); name = "top_short"; }
            case 3 -> { processor.addTopMedium(client.getTopMedium(), userId); name = "top_medium"; }
            case 4 -> { processor.addTopLong(client.getTopLong(), userId); name = "top_long"; }
            case 6 -> { processor.addArtistTopShort(client.getArtistTopShort(), userId); name = "artist_top_short"; }
            case 7 -> { processor.addArtistTopMedium(client.getArtistTopMedium(), userId); name = "artist_top_medium"; }
            case 8 -> { processor.addArtistTopLong(client.getArtistTopLong(), userId); name = "artist_top_long"; }
            default -> throw new IllegalArgumentException("Tabla no válida.");
        }
        System.out.println(name + " actualizado para " + userId + ".");
    }

    private int readOption(int maximum) {
        return readOption(maximum, "Introduce un número entre 0 y " + maximum + ".");
    }

    private int readOption(int maximum, String invalidOptionMessage) {
        while (input.hasNextLine()) {
            String line = input.nextLine().trim();
            try {
                int value = Integer.parseInt(line);
                if (value >= 0 && value <= maximum) return value;
            } catch (NumberFormatException ex) {
                // Se vuelve a pedir la opción sin interrumpir el programa.
            }
            System.out.println(invalidOptionMessage);
        }
        return 0;
    }

    protected SpotifyLogin createLogin(MusicDatabase database) {
        return new SpotifyLogin(new SpotifyAuth(), database);
    }

    protected SpotifyClient createClient(String token) {
        return new SpotifyClient(token);
    }

    protected ReccoBeats createReccoBeats() {
        return new ReccoBeats();
    }
}
