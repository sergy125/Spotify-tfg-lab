package audiofeatures;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.net.ssl.SSLSession;

/** Pruebas de reintentos sin red ni esperas reales. Ejecutar con -ea. */
public class ReccoBeatsRetryTest {
    record Response(int statusCode, HttpHeaders headers, String body) implements HttpResponse<String> {
        public HttpRequest request() { return null; }
        public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        public Optional<SSLSession> sslSession() { return Optional.empty(); }
        public URI uri() { return URI.create("https://api.reccobeats.com/v1/track?ids=test-id"); }
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
    static Response response(int status, String retryAfter) {
        return new Response(status, HttpHeaders.of(retryAfter == null ? Map.of()
                : Map.of("Retry-After", List.of(retryAfter)), (name, value) -> true), "{\"content\":[]}");
    }
    static class FakeRecco extends ReccoBeats {
        final Deque<Response> responses = new ArrayDeque<>();
        final List<Duration> waits = new ArrayList<>();
        final List<URI> urls = new ArrayList<>();
        boolean interrupt;
        HttpResponse<String> send(HttpRequest request) {
            urls.add(request.uri()); return responses.removeFirst();
        }
        void pause(Duration delay) throws InterruptedException {
            waits.add(delay);
            if (interrupt) throw new InterruptedException("Cancelado");
        }
    }
    public static void main(String[] args) throws Exception {
        FakeRecco api = new FakeRecco();
        for (int i = 0; i < 7; i++) api.responses.add(response(429, null));
        api.responses.add(response(200, null));
        assert api.get("/track?ids=test-id").getAsJsonArray("content").isEmpty();
        assert api.waits.stream().map(Duration::toSeconds).toList().equals(List.of(5L, 10L, 20L, 40L, 60L, 60L, 60L));
        assert api.urls.size() == 8 && new HashSet<>(api.urls).size() == 1;
        FakeRecco headers = new FakeRecco();
        headers.responses.add(response(429, "12"));
        headers.responses.add(response(429, "0"));
        headers.responses.add(response(429, "invalid"));
        headers.responses.add(response(429, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(30))));
        headers.responses.add(response(200, null));
        headers.get("/track?ids=test-id");
        assert headers.waits.get(0).toSeconds() == 12;
        assert headers.waits.get(1).toSeconds() == 1;
        assert headers.waits.get(2).toSeconds() == 20;
        assert headers.waits.get(3).toSeconds() >= 28 && headers.waits.get(3).toSeconds() <= 30;
        FakeRecco missing = new FakeRecco(); missing.responses.add(response(404, null));
        assert missing.get("/track?ids=test-id") == null && missing.waits.isEmpty();
        FakeRecco failure = new FakeRecco(); failure.responses.add(response(500, null));
        try { failure.get("/track?ids=test-id"); throw new AssertionError(); }
        catch (java.io.IOException expected) { assert expected.getMessage().contains("500"); }
        FakeRecco cancelled = new FakeRecco(); cancelled.interrupt = true;
        cancelled.responses.add(response(429, null));
        try { cancelled.get("/track?ids=test-id"); throw new AssertionError(); }
        catch (InterruptedException expected) { assert cancelled.urls.size() == 1; }
        System.out.println("PASS: same-query retry until success, progressive delay, Retry-After seconds/date, interruption, unchanged 404/500 handling.");
    }
}
