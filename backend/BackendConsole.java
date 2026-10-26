import database.MusicDatabase;
import models.User;
import processor.SpotifyProcessor;
import spotify.SpotifyAuth;
import spotify.SpotifyClient;
import spotify.SpotifyLogin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/** Menú de cuentas y actualización de datos del backend de terminal. */
public class BackendConsole {
    private final Path databasePath;
    private final MusicDatabase database;
    private final Scanner input;
    private SpotifyLogin login;

    public BackendConsole() {
        this(Files.isDirectory(Path.of("backend", "database"))
                ? Path.of("backend", "database", "lab") : Path.of("database", "lab"),
                new Scanner(System.in));
    }

    BackendConsole(Path databasePath, Scanner input) {
        this.databasePath = databasePath;
        this.database = new MusicDatabase(databasePath);
        this.input = input;
    }

    public void start() throws SQLException, InterruptedException {
        database.createAllTables();
        while (true) {
            List<String> users = listUsers();
            System.out.println("\n¿Qué cuenta de Spotify quieres usar de la tabla Users?");
            for (int i = 0; i < users.size(); i++) {
                System.out.println((i + 1) + ". " + users.get(i));
            }
            if (users.isEmpty()) System.out.println("No hay usuarios registrados.");
            int newAccount = users.size() + 1;
            System.out.println(newAccount + ". Conectar un nuevo Spotify (o volver a autorizar una cuenta)");
            System.out.println("0. Salir");
            int choice = readOption(newAccount);
            if (choice == 0) return;
            try {
                if (login == null) login = createLogin(database);
                User user = choice == newAccount ? login.login()
                        : login.getValidUser(users.get(choice - 1));
                System.out.println("Usuario conectado: " + user.getUserId());
                updateMenu(user.getUserId());
            } catch (IOException | IllegalArgumentException ex) {
                System.out.println("No se pudo conectar la cuenta. Revisa la conexión y las variables "
                        + "SPOTIFY_CLIENT_ID y SPOTIFY_CLIENT_SECRET. Si la autorización ha caducado "
                        + "o se ha revocado, elige conectar un nuevo Spotify para autorizarla de nuevo.");
            }
        }
    }

    // La clase de base de datos actual no expone un listado de usuarios.
    // Consultamos únicamente sus identificadores sin modificar esa clase.
    private List<String> listUsers() throws SQLException {
        List<String> users = new ArrayList<>();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
             var statement = connection.prepareStatement("SELECT user_id FROM Users ORDER BY user_id");
             var result = statement.executeQuery()) {
            while (result.next()) users.add(result.getString("user_id"));
        }
        return users;
    }

    private void updateMenu(String userId) throws InterruptedException {
        SpotifyProcessor processor = new SpotifyProcessor(database);
        while (true) {
            System.out.println("\n¿Qué tabla quieres actualizar para " + userId + "?");
            System.out.println("1. Historial\n2. top_short\n3. top_medium\n4. top_long"
                    + "\n5. Todas las anteriores\n0. Volver a las cuentas");
            System.out.println("Songs se completa automáticamente con las canciones nuevas.");
            int choice = readOption(5);
            if (choice == 0) return;
            try {
                if (choice == 5) {
                    for (int table = 1; table <= 4; table++) update(table, userId, processor);
                } else {
                    update(choice, userId, processor);
                }
            } catch (IOException | SQLException ex) {
                System.out.println("No se pudo completar la actualización. Las tablas anteriores que "
                        + "se hayan confirmado como actualizadas se conservan. Puedes reintentar o "
                        + "volver a las cuentas y autorizar Spotify de nuevo.");
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
            default -> throw new IllegalArgumentException("Tabla no válida.");
        }
        System.out.println(name + " actualizado para " + userId + ".");
    }

    private int readOption(int maximum) {
        while (input.hasNextLine()) {
            String line = input.nextLine().trim();
            try {
                int value = Integer.parseInt(line);
                if (value >= 0 && value <= maximum) return value;
            } catch (NumberFormatException ex) {
                // Se vuelve a pedir la opción sin interrumpir el programa.
            }
            System.out.println("Introduce un número entre 0 y " + maximum + ".");
        }
        return 0;
    }

    protected SpotifyLogin createLogin(MusicDatabase database) {
        return new SpotifyLogin(new SpotifyAuth(), database);
    }

    protected SpotifyClient createClient(String token) {
        return new SpotifyClient(token);
    }
}
