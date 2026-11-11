package api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import database.MusicDatabase;
import models.User;
import spotify.SpotifyAuth;
import statistics.UserStatistics;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/** API de la app. SyncModule actualiza los datos por separado. */
public class ApiModule {
    private final MusicDatabase database;
    private final SpotifyAuth spotify = new SpotifyAuth();
    private final Gson gson = new Gson();
    private final Map<String, PendingLogin> logins = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private HttpServer server;

    public ApiModule(MusicDatabase database) {
        this.database = database;
    }

    public synchronized void run() throws IOException, SQLException {
        if (server != null) return;
        database.createAllTables();
        int port = Integer.parseInt(System.getenv().getOrDefault("API_PORT", "8888"));
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        http.createContext("/", this::handle);
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        http.start();
        server = http;
        System.out.println("API iniciada en http://127.0.0.1:" + port);
    }

    private void handle(HttpExchange request) throws IOException {
        try {
            String path = request.getRequestURI().getPath();
            String method = request.getRequestMethod();
            if (path.equals("/auth/login") && method.equals("POST")) {
                startLogin(request);
            } else if (path.equals(spotify.getRedirectUri().getPath()) && method.equals("GET")) {
                finishLogin(request);
            } else if (path.equals("/auth/result") && method.equals("GET")) {
                loginResult(request);
            } else if (path.equals("/session") && method.equals("GET")) {
                Session session = authenticate(request);
                if (session != null) send(request, 200, Map.of("userId", session.userId()));
            } else if (path.equals("/statistics") && method.equals("GET")) {
                Session session = authenticate(request);
                if (session != null) statistics(request, session.userId());
            } else {
                send(request, 404, Map.of("error", "Ruta o método no disponible."));
            }
        } catch (IllegalArgumentException invalid) {
            send(request, 400, Map.of("error", "Parámetros no válidos."));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            send(request, 503, Map.of("error", "Petición interrumpida. Vuelve a intentarlo."));
        } catch (SQLException | IOException failure) {
            System.getLogger(ApiModule.class.getName()).log(System.Logger.Level.WARNING,
                    "No se pudo completar la consulta de la API", failure);
            send(request, 503, Map.of("error", "Servicio temporalmente no disponible."));
        } finally {
            request.close();
        }
    }

    private void startLogin(HttpExchange request) throws IOException {
        logins.entrySet().removeIf(entry -> entry.getValue().expired());
        sessions.entrySet().removeIf(entry -> entry.getValue().expired());
        String loginId = UUID.randomUUID().toString();
        String state = UUID.randomUUID().toString();
        logins.put(loginId, new PendingLogin(state));
        send(request, 200, Map.of("loginId", loginId,
                "authorizationUrl", spotify.getAuthorizationUri(state).toString()));
    }

    private void finishLogin(HttpExchange request) throws IOException, SQLException, InterruptedException {
        Map<String, String> query = query(request);
        String state = query.getOrDefault("state", "");
        PendingLogin login = logins.values().stream()
                .filter(pending -> pending.state.equals(state)).findFirst().orElse(null);
        if (login == null || login.expired()) {
            send(request, 400, Map.of("error", "Inicio de sesión desconocido o caducado."));
            return;
        }
        // Un mismo callback solo se procesa una vez, aunque llegue concurrentemente.
        synchronized (login) {
            if (login.used) {
                send(request, 400, Map.of("error", "Este enlace ya se ha utilizado."));
                return;
            }
            String code = query.get("code");
            if (query.containsKey("error") || code == null || code.isBlank()) {
                login.used = true;
                login.status = "denied";
                send(request, 400, Map.of("error", "Conexión rechazada. Inicia otra conexión."));
                return;
            }
            login.used = true;
            login.status = "failed";
            User user = spotify.exchangeCode(code);
            // Serializa el alta de una misma cuenta desde dos conexiones a la API.
            synchronized (database) {
                if (database.getUser(user.getUserId()).isPresent()) database.updateUser(user);
                else database.createUser(user);
            }
            String token = UUID.randomUUID().toString();
            sessions.put(token, new Session(user.getUserId(), Instant.now().plusSeconds(30L * 24 * 3600)));
            login.token = token;
            login.status = "connected";
            send(request, 200, Map.of("message", "Spotify conectado. Ya puedes volver a la app."));
        }
    }

    private void loginResult(HttpExchange request) throws IOException {
        PendingLogin login = logins.get(query(request).getOrDefault("loginId", ""));
        if (login == null || login.expired()) {
            send(request, 404, Map.of("error", "Inicio de sesión desconocido o caducado."));
            return;
        }
        synchronized (login) {
            if (login.token == null) send(request, 200, Map.of("status", login.status));
            else send(request, 200, Map.of("status", login.status, "token", login.token));
        }
    }

    private Session authenticate(HttpExchange request) throws IOException {
        String authorization = request.getRequestHeaders().getFirst("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : "";
        Session session = sessions.get(token);
        if (session == null || session.expired()) {
            sessions.remove(token);
            send(request, 401, Map.of("error", "Inicia sesión para continuar."));
            return null;
        }
        return session;
    }

    private void statistics(HttpExchange request, String userId) throws SQLException, IOException {
        UserStatistics statistics = new UserStatistics(userId, database);
        Map<String, Object> result = new HashMap<>();
        result.put("history", statistics.getHistory());
        result.put("topTracks", statistics.getTopTracks());
        result.put("topArtists", statistics.getTopArtists());
        result.put("closestTaylorSwiftEra", statistics.closestTaylorSwiftEra().orElse(null));
        send(request, 200, result);
    }

    private Map<String, String> query(HttpExchange request) {
        Map<String, String> values = new HashMap<>();
        String query = request.getRequestURI().getRawQuery();
        if (query == null) return values;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            if (values.putIfAbsent(key, value) != null) throw new IllegalArgumentException();
        }
        return values;
    }

    private void send(HttpExchange request, int status, Object result) throws IOException {
        byte[] body = gson.toJson(result).getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        request.getResponseHeaders().set("Cache-Control", "no-store");
        request.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        request.sendResponseHeaders(status, body.length);
        request.getResponseBody().write(body);
    }

    private static class PendingLogin {
        final String state;
        final Instant expiresAt = Instant.now().plusSeconds(600);
        boolean used;
        String status = "pending";
        String token;

        PendingLogin(String state) { this.state = state; }
        boolean expired() { return !Instant.now().isBefore(expiresAt); }
    }

    private record Session(String userId, Instant expiresAt) {
        boolean expired() { return !Instant.now().isBefore(expiresAt); }
    }
}
