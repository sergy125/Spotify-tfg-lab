package es.sergy.servicelab.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {
    @Bean
    RestClient spotifyAccountsRestClient() {
        return client("https://accounts.spotify.com");
    }

    @Bean
    RestClient spotifyRestClient(@Value("${spotify.base-url}") String baseUrl) {
        return client(baseUrl);
    }

    @Bean
    RestClient reccoBeatsRestClient(@Value("${reccobeats.base-url}") String baseUrl) {
        return client(baseUrl);
    }

    private RestClient client(String baseUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE).build();
    }
}
