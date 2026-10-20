package es.sergy.servicelab.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/** Primer paso del laboratorio: autorizar una cuenta y mostrar su access token. */
@RestController
public class SpotifyLoginController {
    private final RestClient accounts;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public SpotifyLoginController(@Qualifier("spotifyAccountsRestClient") RestClient accounts,
            @Value("${spotify.client-id}") String clientId,
            @Value("${spotify.client-secret}") String clientSecret,
            @Value("${spotify.redirect-uri}") String redirectUri) {
        this.accounts = accounts;
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.redirectUri = redirectUri;
    }

    @GetMapping("/spotify/login")
    public ResponseEntity<Void> login(HttpSession session) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configura SPOTIFY_CLIENT_ID y SPOTIFY_CLIENT_SECRET en .env y reinicia.");
        }
        // state vincula la respuesta de Spotify con este navegador durante diez minutos.
        String state = UUID.randomUUID().toString();
        session.setAttribute("spotify.state", state);
        session.setAttribute("spotify.state-expires", Instant.now().plusSeconds(600));
        URI authorization = UriComponentsBuilder.fromUriString("https://accounts.spotify.com/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "user-top-read")
                .queryParam("state", state)
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(authorization)
                .cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/spotify/callback")
    public ResponseEntity<?> callback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error, HttpSession session) {
        String expected = (String) session.getAttribute("spotify.state");
        Instant expires = (Instant) session.getAttribute("spotify.state-expires");
        // Solo se puede usar una vez, incluso si el usuario rechaza el permiso.
        session.removeAttribute("spotify.state");
        session.removeAttribute("spotify.state-expires");
        if (expected == null || !expected.equals(state) || expires == null || !Instant.now().isBefore(expires)) {
            return message(HttpStatus.BAD_REQUEST, "Inicio de sesión no válido. Abre /spotify/login de nuevo.");
        }
        if (error != null || code == null || code.isBlank()) {
            return message(HttpStatus.BAD_REQUEST, "No se autorizó el acceso a Spotify. Puedes intentarlo de nuevo.");
        }

        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        try {
            // El secreto viaja únicamente del servidor Java a Spotify, nunca en la URL.
            Token token = accounts.post().uri("/api/token")
                    .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(Token.class);
            if (token == null || token.access_token() == null || token.access_token().isBlank()
                    || token.expires_in() <= 0) {
                return message(HttpStatus.BAD_GATEWAY, "Spotify devolvió una respuesta no válida.");
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .header("Referrer-Policy", "no-referrer").body(token);
        } catch (RestClientException ex) {
            // No devolvemos el cuerpo de error de Spotify ni imprimimos credenciales.
            return message(HttpStatus.BAD_GATEWAY,
                    "No se pudo obtener el token. Revisa las credenciales y vuelve a /spotify/login.");
        }
    }

    private ResponseEntity<String> message(HttpStatus status, String text) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").body(text);
    }

    // Este ejemplo solo muestra el access token: no guarda ni expone el refresh token.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Token(String access_token, int expires_in) { }
}
