package spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import models.User;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/** Peticiones de autorización a Spotify. No abre el navegador ni accede a SQLite. */
public class SpotifyAuth {
    private final String clientId;
    private final String clientSecret;
    private final URI redirectUri;
    private final HttpClient client;

    public SpotifyAuth() {
        this(System.getenv("SPOTIFY_CLIENT_ID"), System.getenv("SPOTIFY_CLIENT_SECRET"),
                System.getenv().getOrDefault("SPOTIFY_REDIRECT_URI", "http://127.0.0.1:8888/callback"));
    }

    public SpotifyAuth(String clientId, String clientSecret, String redirectUri) {
        this(clientId, clientSecret, redirectUri,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
    }

    // Permite probar las peticiones con un cliente simulado, sin contactar con Spotify.
    SpotifyAuth(String clientId, String clientSecret, String redirectUri, HttpClient client) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("Configura SPOTIFY_CLIENT_ID y SPOTIFY_CLIENT_SECRET.");
        }
        this.redirectUri = URI.create(redirectUri);
        if (!"http".equals(this.redirectUri.getScheme()) || !"127.0.0.1".equals(this.redirectUri.getHost())
                || this.redirectUri.getPort() < 1 || this.redirectUri.getPath().isBlank()
                || this.redirectUri.getRawQuery() != null || this.redirectUri.getFragment() != null
                || this.redirectUri.getUserInfo() != null) {
            throw new IllegalArgumentException("Usa una URI local como http://127.0.0.1:8888/callback.");
        }
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.client = client;
    }

    public URI getRedirectUri() {
        return redirectUri;
    }

    public URI getAuthorizationUri(String state) {
        return URI.create("https://accounts.spotify.com/authorize?response_type=code"
                + "&client_id=" + encode(clientId) + "&redirect_uri=" + encode(redirectUri.toString())
                + "&scope=" + encode("user-top-read user-read-recently-played")
                + "&state=" + encode(state));
    }

    public User exchangeCode(String code) throws IOException, InterruptedException {
        Instant authorizedAt = Instant.now();
        User user = requestToken("grant_type=authorization_code&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri.toString()));
        if (user.getRefreshToken() == null || user.getRefreshToken().isBlank()) {
            throw new IOException("Spotify no devolvió un refresh token. Inicia sesión de nuevo.");
        }
        user.setAuthorizedAt(authorizedAt.toString());
        user.setUserId(getUserId(user.getAccessToken()));
        return user;
    }

    public User refresh(User previous) throws IOException, InterruptedException {
        if (previous.getRefreshToken() == null || previous.getRefreshToken().isBlank()) {
            throw new IOException("No hay refresh token. Inicia sesión de nuevo.");
        }
        User user = requestToken("grant_type=refresh_token&refresh_token=" + encode(previous.getRefreshToken()));
        user.setUserId(previous.getUserId());
        user.setAuthorizedAt(previous.getAuthorizedAt());
        // Spotify no siempre entrega otro refresh token: en ese caso conservamos el anterior.
        if (user.getRefreshToken() == null || user.getRefreshToken().isBlank()) {
            user.setRefreshToken(previous.getRefreshToken());
        }
        return user;
    }

    private User requestToken(String form) throws IOException, InterruptedException {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.ofString(form)).build();
        Instant requestedAt = Instant.now();
        JsonObject json = send(request);
        try {
            String accessToken = json.get("access_token").getAsString();
            long expiresIn = json.get("expires_in").getAsLong();
            if (accessToken.isBlank() || expiresIn <= 0) throw new IllegalArgumentException();
            String refreshToken = json.has("refresh_token") && !json.get("refresh_token").isJsonNull()
                    ? json.get("refresh_token").getAsString() : null;
            return new User(null, accessToken, refreshToken, requestedAt.plusSeconds(expiresIn).toString(), null);
        } catch (RuntimeException ex) {
            throw new IOException("Spotify devolvió tokens no válidos.");
        }
    }

    private String getUserId(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me"))
                .header("Authorization", "Bearer " + accessToken)
                .timeout(Duration.ofSeconds(30)).GET().build();
        JsonObject json = send(request);
        try {
            String id = json.get("id").getAsString();
            if (id.isBlank()) throw new IllegalArgumentException();
            return id;
        } catch (RuntimeException ex) {
            throw new IOException("Spotify no devolvió un identificador de usuario válido.");
        }
    }

    private JsonObject send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            // No mostramos el cuerpo de la respuesta: puede contener datos privados.
            throw new IOException("Spotify respondió HTTP " + response.statusCode()
                    + ". Revisa la configuración y los usuarios autorizados; puede ser necesario iniciar sesión de nuevo.");
        }
        try {
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("Spotify devolvió una respuesta no válida.");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
