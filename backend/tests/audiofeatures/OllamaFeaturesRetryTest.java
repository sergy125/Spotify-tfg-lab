package audiofeatures;

import com.google.gson.*;
import models.Song;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.SSLSession;

/** Reintentos reales del módulo, con HTTP y esperas simulados. Sin claves ni créditos. */
public class OllamaFeaturesRetryTest {
    record Response(int statusCode, HttpHeaders headers, String body) implements HttpResponse<String> {
        public HttpRequest request() { return null; }
        public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        public Optional<SSLSession> sslSession() { return Optional.empty(); }
        public URI uri() { return URI.create("https://generativelanguage.googleapis.com/"); }
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
    static Response response(int status, String retryAfter, String text) {
        JsonObject body = new JsonObject();
        body.addProperty("response", text); body.addProperty("done", true); body.addProperty("done_reason", "stop");
        return new Response(status, HttpHeaders.of(retryAfter == null ? Map.of()
                : Map.of("Retry-After", List.of(retryAfter)), (name, value) -> true), body.toString());
    }
    static class FakeIA extends OllamaFeatures {
        final Deque<Response> responses = new ArrayDeque<>();
        final List<HttpRequest> requests = new ArrayList<>();
        final List<Duration> waits = new ArrayList<>();
        boolean interrupt;
        FakeIA() { super("http://127.0.0.1:11434", "qwen3:1.7b", 20); }
        HttpResponse<String> send(HttpRequest request) {
            requests.add(request); return responses.removeFirst();
        }
        void pause(Duration delay) throws InterruptedException {
            waits.add(delay);
            if (interrupt) throw new InterruptedException("Cancelado");
        }
    }
    static String estimates(int start, int count) {
        JsonArray rows = new JsonArray();
        for (int i = start; i < start + count; i++) {
            JsonObject row = new JsonObject(); row.addProperty("id", "song-" + i);
            for (String field : List.of("acousticness", "danceability", "energy", "instrumentalness", "liveness", "speechiness", "valence"))
                row.addProperty(field, .5);
            row.addProperty("key", 2); row.addProperty("mode", 1); row.addProperty("popularity", 50);
            row.addProperty("tempo", 100); row.addProperty("loudness", -8); rows.add(row);
        }
        return rows.toString();
    }
    public static void main(String[] args) throws Exception {
        OllamaFeatures offline = new OllamaFeatures() {
            @Override HttpResponse<String> send(HttpRequest request) throws IOException {
                throw new java.net.ConnectException("offline");
            }
        };
        try { offline.request(new JsonObject()); throw new AssertionError(); }
        catch (IOException expected) { assert expected.getMessage().contains("ollama serve"); }
        OllamaFeatures timeout = new OllamaFeatures() {
            @Override HttpResponse<String> send(HttpRequest request) throws IOException {
                throw new HttpTimeoutException("timeout");
            }
        };
        try { timeout.request(new JsonObject()); throw new AssertionError(); }
        catch (IOException expected) { assert expected.getMessage().contains("tiempo"); }
        FakeIA api = new FakeIA();
        // Primer grupo: éxito. Segundo: dos 429 y después éxito, sin repetir el primero.
        api.responses.add(response(200, null, estimates(0, 20)));
        api.responses.add(response(429, "7", ""));
        api.responses.add(response(429, null, ""));
        api.responses.add(response(200, null, estimates(20, 1)));
        List<Song> songs = new ArrayList<>();
        for (int i = 0; i < 21; i++) { Song song = new Song(); song.setId("song-" + i); songs.add(song); }
        api.addFeatures(songs);
        assert api.requests.size() == 4;
        assert api.requests.get(0) != api.requests.get(1);
        assert api.requests.get(1) == api.requests.get(2) && api.requests.get(2) == api.requests.get(3);
        assert api.waits.stream().map(Duration::toSeconds).toList().equals(List.of(7L, 10L));
        assert songs.stream().allMatch(song -> !song.hasMissingFeatures() && song.isAi() && song.getPopularidad() == 50);
        FakeIA progressive = new FakeIA();
        for (int i = 0; i < 4; i++) progressive.responses.add(response(429, null, ""));
        try { progressive.request(new JsonObject()); throw new AssertionError(); }
        catch (IOException expected) { assert progressive.requests.size() == 4; }
        assert progressive.waits.stream().map(Duration::toSeconds).toList().equals(List.of(5L, 10L, 20L));
        for (String reason : List.of("length", "cancelled")) {
            FakeIA truncated = new FakeIA();
            JsonObject body = new JsonObject(); body.addProperty("done", true);
            body.addProperty("done_reason", reason); body.addProperty("response", "[]");
            truncated.responses.add(new Response(200, HttpHeaders.of(Map.of(), (n, v) -> true), body.toString()));
            try { truncated.request(new JsonObject()); throw new AssertionError(); }
            catch (IOException expected) { assert expected.getMessage().contains("completo"); }
        }
        FakeIA missing = new FakeIA(); missing.responses.add(response(404, null, ""));
        try { missing.request(new JsonObject()); throw new AssertionError(); }
        catch (IOException expected) { assert expected.getMessage().contains("ollama pull qwen3:1.7b"); }
        FakeIA denied = new FakeIA(); denied.responses.add(response(403, null, ""));
        try { denied.request(new JsonObject()); throw new AssertionError(); }
        catch (IOException expected) { assert denied.waits.isEmpty() && denied.requests.size() == 1; }
        FakeIA cancelled = new FakeIA(); cancelled.interrupt = true; cancelled.responses.add(response(429, null, ""));
        try { cancelled.request(new JsonObject()); throw new AssertionError(); }
        catch (InterruptedException expected) { assert cancelled.requests.size() == 1; }
        FakeIA unavailable = new FakeIA();
        unavailable.responses.add(response(503, "2", ""));
        unavailable.responses.add(response(503, null, ""));
        unavailable.responses.add(response(200, null, "[]"));
        assert unavailable.request(new JsonObject()).equals("[]");
        assert unavailable.waits.equals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(10)));
        assert unavailable.requests.stream().allMatch(request -> request == unavailable.requests.get(0));
        FakeIA exhausted = new FakeIA();
        for (int i = 0; i < 4; i++) exhausted.responses.add(response(503, null, ""));
        try { exhausted.request(new JsonObject()); throw new AssertionError(); }
        catch (IOException expected) {
            assert expected.getMessage().contains("503");
            assert exhausted.requests.size() == 4 && exhausted.waits.size() == 3;
        }
        System.out.println("PASS: Ollama retries same batch, preserves previous batches, parses features after retry, waits progressively, respects Retry-After, stops on interruption/403.");
    }
}
