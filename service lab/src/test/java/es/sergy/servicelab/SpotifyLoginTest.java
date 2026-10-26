package es.sergy.servicelab;

import es.sergy.servicelab.spotify.SpotifyLoginController;
import es.sergy.servicelab.database.MusicDatabase;
import static org.mockito.Mockito.*;
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
        var profileBuilder = RestClient.builder().baseUrl("https://api.spotify.com/v1");
        var profiles = MockRestServiceServer.bindTo(profileBuilder).build();
        var database = mock(MusicDatabase.class);
        var login = new SpotifyLoginController(builder.build(), profileBuilder.build(), database, "id", "secret",
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
        profiles.expect(requestTo("https://api.spotify.com/v1/me"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("{\"id\":\"user-1\",\"display_name\":\"Sergy\"}", MediaType.APPLICATION_JSON));
        var response = login.callback("code", state, null, session);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(((SpotifyLoginController.Token) response.getBody()).access_token()).isEqualTo("test-token");
        assertThat(login.callback("code", state, null, session).getStatusCode().value()).isEqualTo(400);
        server.verify();
        profiles.verify();
        verify(database).saveUser("user-1", "Sergy");
    }

    @Test
    void failedProfileRequestDoesNotSaveUser() {
        var accountsBuilder = RestClient.builder().baseUrl("https://accounts.spotify.com");
        var accounts = MockRestServiceServer.bindTo(accountsBuilder).build();
        var profileBuilder = RestClient.builder().baseUrl("https://api.spotify.com/v1");
        var profiles = MockRestServiceServer.bindTo(profileBuilder).build();
        var database = mock(MusicDatabase.class);
        var login = new SpotifyLoginController(accountsBuilder.build(), profileBuilder.build(), database,
                "id", "secret", "http://127.0.0.1/callback");
        var session = new MockHttpSession();
        login.login(session);
        accounts.expect(requestTo("https://accounts.spotify.com/api/token"))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        profiles.expect(requestTo("https://api.spotify.com/v1/me")).andRespond(withServerError());
        assertThat(login.callback("code", (String) session.getAttribute("spotify.state"), null, session)
                .getStatusCode().value()).isEqualTo(502);
        verifyNoInteractions(database);
        accounts.verify();
        profiles.verify();
    }

    @Test
    void rejectsDenialAndExpiredLoginWithoutCallingSpotify() {
        var login = new SpotifyLoginController(RestClient.create(), RestClient.create(), mock(MusicDatabase.class), "id", "secret", "http://127.0.0.1/callback");
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
