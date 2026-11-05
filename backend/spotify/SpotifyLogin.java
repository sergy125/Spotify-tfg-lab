package spotify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import database.MusicDatabase;
import models.User;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Inicio de sesión con callback local o mediante proxy HTTPS y almacenamiento en SQLite. */
public class SpotifyLogin {
    private final SpotifyAuth auth;
    private final MusicDatabase database;
    private final int callbackPort;

    public SpotifyLogin(SpotifyAuth auth, MusicDatabase database) {
        this(auth, database, Integer.parseInt(System.getenv().getOrDefault("SPOTIFY_CALLBACK_PORT",
                String.valueOf(auth != null && "http".equals(auth.getRedirectUri().getScheme())
                        ? auth.getRedirectUri().getPort() : 8888))));
    }

    public SpotifyLogin(SpotifyAuth auth, MusicDatabase database, int callbackPort) {
        if (callbackPort < 1 || callbackPort > 65535) {
            throw new IllegalArgumentException("SPOTIFY_CALLBACK_PORT debe estar entre 1 y 65535.");
        }
        this.auth = auth;
        this.database = database;
        this.callbackPort = callbackPort;
    }

    public User login() throws IOException, InterruptedException, SQLException {
        URI redirect = auth.getRedirectUri();
        String state = UUID.randomUUID().toString();
        var result = new CompletableFuture<User>();
        // El túnel termina HTTPS y reenvía únicamente a este puerto local.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", callbackPort), 0);
        server.createContext(redirect.getPath(), exchange -> handleCallback(exchange, redirect, state, result));
        server.start();
        try {
            URI authorization = auth.getAuthorizationUri(state);
            boolean publicLogin = "https".equals(redirect.getScheme());
            System.out.println(publicLogin ? "Envía este enlace al dispositivo que va a iniciar sesión:"
                    : "Inicia sesión en Spotify. Si el navegador no se abre, visita:");
            System.out.println(authorization);
            if ("https".equals(redirect.getScheme())) {
                System.out.println("Puedes enviar este enlace a otro dispositivo. Mantén el túnel HTTPS abierto.");
            } else {
                System.out.println("Modo local: este enlace solo funciona en este ordenador. "
                        + "Configura SPOTIFY_REDIRECT_URI con una URL HTTPS pública para usar un móvil.");
            }
            try {
                if (!publicLogin && Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(authorization);
                }
            } catch (IOException | UnsupportedOperationException | SecurityException ex) {
                System.out.println("Abre el enlace anterior manualmente.");
            }
            return result.get(10, TimeUnit.MINUTES);
        } catch (TimeoutException ex) {
            throw new IOException("Tiempo de inicio de sesión agotado. Vuelve a intentarlo.");
        } catch (ExecutionException ex) {
            throw new IOException("No se autorizó el acceso a Spotify. Vuelve a iniciar sesión.");
        } finally {
            server.stop(0);
        }
    }

    /** Recupera una cuenta guardada y renueva su acceso si es necesario. */
    public User getValidUser(String userId) throws SQLException, IOException, InterruptedException {
        database.createUsersTable();
        User user = database.getUser(userId)
                .orElseThrow(() -> new IOException("Usuario no guardado. Usa login() primero."));
        try {
            if (user.getAccessToken() != null && !user.getAccessToken().isBlank()
                    && user.getExpiresAt() != null
                    && Instant.now().plusSeconds(60).isBefore(Instant.parse(user.getExpiresAt()))) {
                return user;
            }
        } catch (DateTimeParseException ex) {
            // Si la fecha guardada no es válida, intentamos renovar el token.
        }
        User renewed = auth.refresh(user);
        database.updateUser(renewed);
        return renewed;
    }

    private void handleCallback(HttpExchange exchange, URI redirect, String state,
                                CompletableFuture<User> result) throws IOException {
        User savedUser = null;
        Exception failure = null;
        int status = 400;
        String message = "Respuesta no válida. Vuelve al enlace de inicio de sesión.";
        try {
            if (exchange.getRequestMethod().equals("GET")
                    && exchange.getRequestURI().getPath().equals(redirect.getPath())) {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                if (state.equals(query.get("state")) && !result.isDone()) {
                    if (query.containsKey("error")) {
                        failure = new IOException("Autorización rechazada.");
                        message = "Acceso rechazado. Puedes volver al programa.";
                    } else if (query.containsKey("code") && !query.get("code").isBlank()) {
                        savedUser = auth.exchangeCode(query.get("code"));
                        database.createUsersTable();
                        if (database.getUser(savedUser.getUserId()).isPresent()) database.updateUser(savedUser);
                        else database.createUser(savedUser);
                        status = 200;
                        message = "Cuenta conectada y guardada en el backend. Ya puedes cerrar esta página.";
                    }
                }
            }
        } catch (IOException | SQLException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            failure = ex;
            status = 502;
            message = "No se pudo guardar la cuenta. Vuelve a iniciar sesión desde el backend.";
        } catch (IllegalArgumentException ex) {
            // Una URL mal formada no debe interrumpir la espera del usuario.
        }
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        } finally {
            exchange.close();
            if (failure != null) result.completeExceptionally(failure);
            else if (savedUser != null) result.complete(savedUser);
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> values = new HashMap<>();
        if (query == null) return values;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            if (values.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Parámetro repetido.");
        }
        return values;
    }
}
