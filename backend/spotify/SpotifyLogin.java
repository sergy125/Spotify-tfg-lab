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

/** Inicio de sesión local y almacenamiento del usuario mediante MusicDatabase. */
public class SpotifyLogin {
    private final SpotifyAuth auth;
    private final MusicDatabase database;

    public SpotifyLogin(SpotifyAuth auth, MusicDatabase database) {
        this.auth = auth;
        this.database = database;
    }

    public User login() throws IOException, InterruptedException, SQLException {
        URI redirect = auth.getRedirectUri();
        String state = UUID.randomUUID().toString();
        var result = new CompletableFuture<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", redirect.getPort()), 0);
        server.createContext(redirect.getPath(), exchange -> handleCallback(exchange, redirect, state, result));
        server.start();
        try {
            URI authorization = auth.getAuthorizationUri(state);
            System.out.println("Inicia sesión en Spotify. Si el navegador no se abre, visita:");
            System.out.println(authorization);
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(authorization);
                }
            } catch (IOException | UnsupportedOperationException | SecurityException ex) {
                System.out.println("Abre el enlace anterior manualmente.");
            }
            String code = result.get(3, TimeUnit.MINUTES);
            User user = auth.exchangeCode(code);
            database.createUsersTable();
            if (database.getUser(user.getUserId()).isPresent()) database.updateUser(user);
            else database.createUser(user);
            return user;
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
                                CompletableFuture<String> result) throws IOException {
        int status = 400;
        String message = "Respuesta no válida. Vuelve al enlace de inicio de sesión.";
        try {
            if (exchange.getRequestMethod().equals("GET")
                    && exchange.getRequestURI().getPath().equals(redirect.getPath())) {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                if (state.equals(query.get("state")) && !result.isDone()) {
                    if (query.containsKey("error")) {
                        result.completeExceptionally(new IOException("Autorización rechazada."));
                        message = "Acceso rechazado. Puedes volver al programa.";
                    } else if (query.containsKey("code") && !query.get("code").isBlank()) {
                        result.complete(query.get("code"));
                        status = 200;
                        message = "Autorización recibida. Vuelve al programa para ver el resultado.";
                    }
                }
            }
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
