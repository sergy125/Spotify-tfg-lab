package spotify;

import com.sun.net.httpserver.HttpServer;
import database.MusicDatabase;
import models.User;
import java.net.*;
import java.net.http.*;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/** Callback remoto simulado: no contacta con Spotify ni modifica la base real. */
public class SpotifyRemoteLoginTest {
    public static void main(String[] args) throws Exception {
        String redirect = "https://example.com/callback";
        var auth = new SpotifyAuth("id", "secret", redirect) {
            @Override public User exchangeCode(String code) {
                assert code.equals("test-code");
                return new User("remote-user", "private-token", "private-refresh", "2099-01-01T00:00:00Z", null);
            }
        };
        assert auth.getAuthorizationUri("state").toString().contains("https%3A%2F%2Fexample.com%2Fcallback");
        new SpotifyAuth("id", "secret", "http://127.0.0.1:8888/callback");
        for (String invalid : new String[]{"http://192.168.1.2:8888/callback", "https://example.com/callback?x=1"}) {
            try { new SpotifyAuth("id", "secret", invalid); throw new AssertionError(invalid); }
            catch (IllegalArgumentException expected) { }
        }
        var db = new MusicDatabase(Files.createTempFile("remote-login-", ".sqlite"));
        var login = new SpotifyLogin(auth, db, 8888);
        var result = new CompletableFuture<User>();
        var method = SpotifyLogin.class.getDeclaredMethod("handleCallback",
                com.sun.net.httpserver.HttpExchange.class, URI.class, String.class, CompletableFuture.class);
        method.setAccessible(true);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/callback", exchange -> {
            try { method.invoke(login, exchange, URI.create(redirect), "expected-state", result); }
            catch (ReflectiveOperationException ex) { throw new RuntimeException(ex); }
        });
        server.start();
        try {
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/callback";
            var invalid = client.send(HttpRequest.newBuilder(URI.create(base + "?code=test-code&state=wrong")).build(), HttpResponse.BodyHandlers.ofString());
            assert invalid.statusCode() == 400 && !result.isDone();
            var response = client.send(HttpRequest.newBuilder(URI.create(base + "?code=test-code&state=expected-state")).build(), HttpResponse.BodyHandlers.ofString());
            assert response.statusCode() == 200;
            assert response.body().contains("guardada") && !response.body().contains("private-token");
            assert result.get().getUserId().equals("remote-user");
            assert db.getUser("remote-user").orElseThrow().getRefreshToken().equals("private-refresh");
            var replay = client.send(HttpRequest.newBuilder(URI.create(base + "?code=test-code&state=expected-state")).build(), HttpResponse.BodyHandlers.ofString());
            assert replay.statusCode() == 400;
        } finally { server.stop(0); }
        System.out.println("SpotifyRemoteLoginTest OK");
    }
}
