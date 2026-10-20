package es.sergy.servicelab;

import es.sergy.servicelab.spotify.SpotifyLoginController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SpotifyLoginTest {
    @Test
    void exchangesCodeOnlyForTheBrowserThatStartedLoginAndOnlyOnce() {
        var builder = RestClient.builder().baseUrl("https://accounts.spotify.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var login = new SpotifyLoginController(builder.build(), "id", "secret",
                "http://127.0.0.1:8080/spotify/callback");
        var session = new MockHttpSession();
        var redirect = login.login(session);
        String state = (String) session.getAttribute("spotify.state");
        assertThat(redirect.getHeaders().getLocation().toString())
                .contains("response_type=code", "scope=user-top-read", "state=" + state)
                .doesNotContain("secret");
        assertThat(login.callback("code", state, null, new MockHttpSession()).getStatusCode().value())
                .isEqualTo(400);
        server.expect(requestTo("https://accounts.spotify.com/api/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic aWQ6c2VjcmV0"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
                .andRespond(withSuccess("{\"access_token\":\"test-token\",\"expires_in\":3600,\"refresh_token\":\"private\"}",
                        MediaType.APPLICATION_JSON));
        var response = login.callback("code", state, null, session);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(((SpotifyLoginController.Token) response.getBody()).access_token()).isEqualTo("test-token");
        assertThat(login.callback("code", state, null, session).getStatusCode().value()).isEqualTo(400);
        server.verify();
    }

    @Test
    void rejectsDenialAndExpiredLoginWithoutCallingSpotify() {
        var login = new SpotifyLoginController(RestClient.create(), "id", "secret", "http://127.0.0.1/callback");
        var session = new MockHttpSession();
        login.login(session);
        String state = (String) session.getAttribute("spotify.state");
        assertThat(login.callback(null, state, "access_denied", session).getStatusCode().value()).isEqualTo(400);
        login.login(session);
        state = (String) session.getAttribute("spotify.state");
        session.setAttribute("spotify.state-expires", java.time.Instant.now().minusSeconds(1));
        assertThat(login.callback("code", state, null, session).getStatusCode().value()).isEqualTo(400);
    }
}
